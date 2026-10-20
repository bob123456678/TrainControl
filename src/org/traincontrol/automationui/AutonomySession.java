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
import org.traincontrol.automationui.TileGraph.Run;
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
    final AutonomyCompanionStore store;

    private List<LayoutDiagram> pages = new ArrayList<>();
    TileGraph graph;
    // VOLATILE, AND HANDED OUT ONLY ONCE REDUCED (OB-298, ADA-C2) - see `rebuild`.
    volatile GraphReducer reducer;

    boolean dirty = false;

    /** Imports a legacy autonomy.json or an exported bundle into this session (FR-136). */
    private final SessionImport sessionImport = new SessionImport(this);

    /** The runs of plain track, a direction set along one, and the marks drawn for them (FR-136). */
    private final SessionRuns sessionRuns = new SessionRuns(this);

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
     * A rename rekeys the STORE and writes a new diagram file, but cannot rename the LayoutDiagram objects this session
     * holds, so until the next `open` the store's naming and everything derived from the pages disagree.  Only
     * `captureFromLayout` reads the naming in that window, and it refuses while this is set.
     */
    private boolean pagesStale = false;

    /**
     * Whether squares have been moved, removed or built over since the railway running was built (RSA6-B2, RSA6-A2,
     * RSA7-A1): the fold is refused until the next `open`, as after a page rename.  That railway names and places what
     * it knows by the squares as they were, so a fold would write the old names back over the moved ones, or judge the
     * squares a Cancel put back against the page the editor had emptied.  Nothing runs while the editor is open, so
     * nothing the refusal keeps out is new.
     */
    private boolean squaresMoved = false;

    /**
     * A page renamed: the setup's squares on it, and the railway's (RSA7-B3).
     *
     * @param from the page's name as it was
     * @param to its name now
     */
    public void renamePage(String from, String to)
    {
        store.renamePage(from, to);

        markPagesStale();

        moveTheRailwaysSquares(square -> from.equals(square.getPage()) ? new TileKey(to, square.getX(), square.getY())
            : square);

        // NOT WHAT THE CARRY REMEMBERS: until the next `open` this session's pages still carry the old name, and a build
        // meanwhile names its squares by it (see `pagesStale`)
    }

    /**
     * The squares the railway running says its Points are copies of, moved as the setup's are (RSA7-B3).
     *
     * While an edit declined during a run waits, a load carries each train by the square the railway says it stands on,
     * so a move or a page rename must move the railway's squares too, or the carry reads each train's square as it was.
     *
     * @param to each square, to where it is now
     */
    private void moveTheRailwaysSquares(java.util.function.UnaryOperator<TileKey> to)
    {
        org.traincontrol.automation.Layout running = runningLayout == null ? null : runningLayout.get();

        if (running == null) return;

        for (org.traincontrol.automation.Point point : running.getPoints())
        {
            TileKey square = point.getSquare() == null ? null : AutonomyCompanionStore.parseTileKey(point.getSquare());

            TileKey now = square == null ? null : to.apply(square);

            if (now == null || now.equals(square)) continue;

            point.setSquare(now.toString());
        }
    }

    /**
     * What an edit of the track changes outside the setup of its page (RSA8-B2, RSA8-B3): what the carry remembers of
     * the builds it has seen, the squares the railway running says its Points are copies of, and the names in every
     * configuration's stored legs.  A page's undo point holds it, and the session holds it as it was before the first
     * move, for a Cancel; each is put back with the setup, because a move changes them in step with the squares.
     */
    private static final class Remembered
    {
        private final Map<String, TileKey> squares;
        private final Map<String, String> bases;
        private final Map<String, Side> facings;
        private final Map<org.traincontrol.automation.Point, String> railway;
        private final Map<String, Map<String, String[]>> legs;

        Remembered(Map<String, TileKey> squares, Map<String, String> bases, Map<String, Side> facings,
            Map<org.traincontrol.automation.Point, String> railway, Map<String, Map<String, String[]>> legs)
        {
            this.squares = squares;
            this.bases = bases;
            this.facings = facings;
            this.railway = railway;
            this.legs = legs;
        }
    }

    /** Where a page's undo point holds what the edit changes outside the page - see `Remembered`. */
    private static final String REMEMBERED = "session";

    /**
     * What the carry remembered, and the railway's squares, as the editor opened - or before the first move where no
     * editor said it opened - for a Cancel (RSA8-B2, RSA9-B2).  Taken at the first move alone, a Cancel after two names
     * were swapped would find each remembered on the other's square.
     */
    private Remembered asTheEditBegan;

    /** What this session remembers now - see `Remembered`. */
    private Remembered remembered()
    {
        Map<org.traincontrol.automation.Point, String> railway = new java.util.IdentityHashMap<>();

        org.traincontrol.automation.Layout running = runningLayout == null ? null : runningLayout.get();

        for (org.traincontrol.automation.Point point : running == null
            ? Collections.<org.traincontrol.automation.Point>emptyList() : running.getPoints())
        {
            if (point.getSquare() != null) railway.put(point, point.getSquare());
        }

        Map<String, Map<String, String[]>> legs = new LinkedHashMap<>();

        for (String name : store.getConfigurationNames())
        {
            org.json.JSONObject configuration = store.getConfiguration(name);

            if (configuration == null) continue;

            Map<String, String[]> names = new LinkedHashMap<>();

            for (Map.Entry<String, NamedLeg> leg : keyedLegsOf(configuration).entrySet())
            {
                names.put(leg.getKey(), new String[] {leg.getValue().get(0), leg.getValue().get(1)});
            }

            legs.put(name, names);
        }

        return new Remembered(new java.util.HashMap<>(squareOfNameSeen), new java.util.HashMap<>(baseOfNameSeen),
            new java.util.HashMap<>(facingOfNameSeen), railway, legs);
    }

    /**
     * Puts back what this session remembered - and the stored legs where asked, each by where it is (RSA9-C1): a road's
     * by its square and step, and the timetable's by entry and leg where the timetable has as many legs as it had.  By
     * position, two roads whose squares share a bucket were written into each other, the page's points being put back
     * first as a new map.
     */
    private void putBack(Remembered was, boolean legsToo)
    {
        squareOfNameSeen.clear();
        squareOfNameSeen.putAll(was.squares);

        baseOfNameSeen.clear();
        baseOfNameSeen.putAll(was.bases);

        facingOfNameSeen.clear();
        facingOfNameSeen.putAll(was.facings);

        for (Map.Entry<org.traincontrol.automation.Point, String> said : was.railway.entrySet())
        {
            said.getKey().setSquare(said.getValue());
        }

        if (!legsToo) return;

        for (Map.Entry<String, Map<String, String[]>> each : was.legs.entrySet())
        {
            org.json.JSONObject configuration = store.getConfiguration(each.getKey());

            Map<String, NamedLeg> now = configuration == null ? Collections.<String, NamedLeg>emptyMap()
                : keyedLegsOf(configuration);

            boolean sameTimetable = timetableLegs(now.keySet()) == timetableLegs(each.getValue().keySet());

            for (Map.Entry<String, String[]> leg : each.getValue().entrySet())
            {
                NamedLeg there = now.get(leg.getKey());

                if (there == null || leg.getKey().startsWith("t") && !sameTimetable) continue;

                there.set(0, leg.getValue()[0]);
                there.set(1, leg.getValue()[1]);

                dirty = true;
            }
        }
    }

    /**
     * A page deleted: its squares forgotten in every configuration, and these pages marked stale (RSA22-C2), as a rename
     * marks them - the fold that follows the delete, behind the reset, worked its keys out from these pages, the deleted
     * one among them, and wrote its trains and its stations' settings straight back under its name.  The delete door
     * folds first (DW-C1), so nothing the refusal keeps out is new.
     *
     * @param page the page's name
     * @return how many entries were forgotten
     */
    public int deletePage(String page)
    {
        int forgotten = store.deletePage(page);

        markPagesStale();

        return forgotten;
    }

    /**
     * Says that a page has been renamed or deleted and these page objects no longer describe the setup.
     *
     * Called by the rename and the delete themselves. Cleared by the next `open`, which is what makes it true again.
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
        this.squaresMoved = false;

        this.pages = diagrams == null ? new ArrayList<LayoutDiagram>() : new ArrayList<>(diagrams);

        Map<String, String> pageIds = new LinkedHashMap<>();

        for (LayoutDiagram page : pages)
        {
            // A placeholder's id is NOT offered to the store (FV3-A1): `pageIsHere` answers from this map, and answering
            // yes about a page whose contents did not load would release the entries OB-067 holds out of memory to be
            // written back verbatim.  The placeholder keeps its id for the index writer ("which page is this"); this
            // map answers "is its track here to judge".
            if (page.isUnreadable()) continue;

            if (page.getPageId() != null) pageIds.put(page.getName(), page.getPageId());
        }

        store.setPageIds(pageIds);
        store.load();

        rebuild();

        // EVERY SQUARE RUNS AS ITS ARROWS SHOW (OB-351), and every run one way (OB-361), and the setup is saved that way at
        // once, so each is said once
        broughtToTheirArrows = sentencesForWhatWasBrought();
        arrowsSaveFailure = null;
        arrowsSaveFailureToSay = false;

        if (!broughtToTheirArrows.isEmpty())
        {
            try
            {
                store.save();

                // Said only of a save that happened
                for (Map.Entry<TileKey, String> said : broughtToTheirArrows.entrySet())
                {
                    said.setValue(said.getValue() + "  " + I18n.t("autosetup.log.followsItsArrowsSaved"));
                }
            }
            catch (IOException e)
            {
                // Not saved: the railway still runs as the arrows show, the next open brings them to it and says so again,
                // and the log says the setup was not saved, and why
                arrowsSaveFailure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                arrowsSaveFailureToSay = true;
            }
        }

        // A ROUTE TILE'S LENGTH GOES TO THE TRACK BESIDE IT, once, as the setup is read.
        foldedRouteTiles = foldRouteTileLengths();

        if (!foldedRouteTiles.isEmpty()) rebuild();

        // Captions still written in the layout file are brought across now, once (`migrateStationLabels`).  Its failures
        // are kept for the UI to report, not thrown: a page that could not be rewritten is no reason to refuse to open
        // the setup, and the migration runs again next time.
        migratedPages.clear();
        migratedCaptions = 0;

        migrationFailures = migrateStationLabels();

        // NO CAPTION IS FORGOTTEN HERE FOR NAMING A SQUARE THAT IS NOT A STATION NOW (GSE-C4): demoting keeps it, ignored
        // while not a station, and `getCaptionTarget` leaves it undrawn until the square is one again.

        dirty = false;
    }

    /** The squares brought to their arrows as this setup was opened (OB-351), each to a sentence for the log. */
    private Map<TileKey, String> broughtToTheirArrows = new LinkedHashMap<>();

    /**
     * The squares whose directions this setup was opened with ran differently from their arrows, and now run as the
     * arrows show (OB-351; Adam: *"Align it so that the arrows tell the full story."*), each to a sentence for the log.
     * The setup is saved that way as it is opened, so a square is here once.
     *
     * @return square to sentence, empty when every square already ran as its arrows show
     */
    public Map<TileKey, String> getSquaresBroughtToTheirArrows()
    {
        return Collections.unmodifiableMap(broughtToTheirArrows);
    }

    /**
     * Why the setup could not be saved as it opened with squares brought to their arrows, or null.
     *
     * @return the reason, or null when it was saved or nothing needed saving
     */
    public String getArrowsSaveFailure()
    {
        return arrowsSaveFailure;
    }

    /** Why the setup could not be saved as it opened with squares brought to their arrows, or null (C2). */
    private String arrowsSaveFailure;

    /** Whether that failure is still to be said. */
    private boolean arrowsSaveFailureToSay;

    /**
     * The sentences for the squares brought to their arrows, handed over once: the window logs them and they are not said
     * again by the next door that asks.
     *
     * @return one sentence per square, empty when there is nothing new to say
     */
    public List<String> takeSquaresBroughtToTheirArrows()
    {
        List<String> out = new ArrayList<>(broughtToTheirArrows.values());

        // AND THAT THE SETUP COULD NOT BE SAVED THAT WAY, with the reason, in place of saying it was
        if (arrowsSaveFailureToSay)
        {
            out.add(I18n.f("autosetup.log.followsItsArrowsNotSaved", arrowsSaveFailure));

            arrowsSaveFailureToSay = false;
        }

        broughtToTheirArrows = new LinkedHashMap<>();

        return out;
    }

    /**
     * The sentences for what the graph last brought to its arrows: a square for each switch (OB-351), and one for each run
     * brought to one direction, under its first square (OB-361).
     *
     * @return square to sentence
     */
    private Map<TileKey, String> sentencesForWhatWasBrought()
    {
        Map<TileKey, String> out = sentencesFor(graph.getNormalizedToArms().keySet());

        for (Map.Entry<TileKey, Map<TileKey, Map<RouteId, Direction>>> brought : graph.getNormalizedRuns().entrySet())
        {
            TileKey leader = brought.getKey();

            // NOT A SQUARE JUST BUILT OVER taking its run's direction: that is the edit, said by nothing (OB-361)
            if (joiningTheirRuns.containsAll(brought.getValue().keySet())) continue;

            Run run = graph.runs().get(leader);

            Direction now = graph.runsAs(leader);

            if (run == null || now == null) continue;

            List<TileKey> tiles = run.getTiles();

            TileKey last = tiles.get(tiles.size() - 1);

            // ONE WAY, toward the end its first square's direction leads to; or closed
            String way;

            if (now == Direction.NONE)
            {
                way = I18n.t("autosetup.log.runClosed");
            }
            else
            {
                Route road = graph.getRoutes(leader).values().iterator().next();

                Side toward = now == Direction.TOWARD_A ? road.getA() : road.getB();

                TileKey end = toward == graph.sideTowardNeighbour(leader, tiles.get(1)) ? last : leader;

                way = I18n.f("autosetup.log.runOneWay", end.getX() + "," + end.getY());
            }

            out.put(leader, I18n.f("autosetup.log.runBroughtIntoLine", leader.getX() + "," + leader.getY(),
                last.getX() + "," + last.getY(), leader.getPage(), way));
        }

        return out;
    }

    /**
     * A sentence for each square brought to its arrows: what it is, where, and the arms trains may now leave it by.
     *
     * @param squares the squares
     * @return square to sentence
     */
    private Map<TileKey, String> sentencesFor(java.util.Collection<TileKey> squares)
    {
        Map<TileKey, String> out = new LinkedHashMap<>();

        for (TileKey square : squares)
        {
            LayoutDiagramComponent component = graph.getTiles().get(square);

            String named = store.getPointName(square);

            String what = named != null && !named.trim().isEmpty() ? named.trim()
                : component != null && !component.toSimpleString().isEmpty() ? component.toSimpleString()
                : I18n.t("autosetup.log.followsItsArrowsSquare");

            List<Side> arms = graph.armsOf(square);

            int mask = graph.armMask(square);

            List<String> open = new ArrayList<>();

            for (int i = 0; i < arms.size(); i++)
            {
                if ((mask & (1 << i)) != 0) open.add(String.valueOf(arms.get(i)));
            }

            out.put(square, I18n.f("autosetup.log.followsItsArrows", what, square.getX() + "," + square.getY(),
                square.getPage(), String.join(", ", open)));
        }

        return out;
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
     * Moves any length a route tile holds onto the track beside it, keeping the total.
     *
     * A route tile takes no length (OB-273: *"it just implicitly connects things as if it were a crossing"*), but the
     * room and tail sums still add whatever it holds (`GraphReducer.sumLength` and `lengthOf` read every step, AUT-C4),
     * so a unit left there is counted without being anywhere the editor shows or asks about.  Each goes to the square
     * beside the route tile along the road it carries - plain track in preference to a switch, whose length is the
     * page's one turnout length - and the sums come out as they were.
     *
     * **Never onto a sensor square** (AUT-C4): a sensor's length is the last place of every rail arriving at it, so a
     * unit put there lengthens every other approach too.
     *
     * Run as the setup is opened.  A route tile with no plain track or switch beside it keeps its length.
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
     * Not the editor's undo, which is `snapshotPage`/`restorePage` through the companion store (VD9-C16).  The one
     * caller is `LayoutEditor.forgetCaptionsOutsideThePage`, the `RC-B1` cleanup that runs when a page is shrunk; it
     * wants the keys and not a round trip.
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
            // Only squares this setup can still SEE: the index is derived from the graph, which leaves out excluded pages
            // and a station fed through a link, so pruning against it would read "every side of this station has gone"
            // and delete the restriction outright.  Excluding a page has to be reversible.
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
     * The migration edits files the user owns - a `Point:` label typed onto their own track diagram is taken into the
     * setup and emptied out of the `.cs2` - so the user has to be told.  `LayoutDiagram.saveChanges` keeps a `.cs2.bak`
     * the first time it rewrites a page, so the names are recoverable.
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
     * Every edit goes straight into the live configuration this session hands out, so there is no copy to go back to;
     * re-reading is the discard.  What is on disk is by definition the last state the user agreed to, and rebuilding
     * from it cannot leave a half-reverted graph the way replaying edits backwards could.  Station captions are part of
     * the setup and are taken back with the rest.
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
        // Always creates one: this is "add a configuration" as well as the first-time setup.  A name already in use
        // throws, as for a duplicate - the caller says so.
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
        // The index is NOT nulled here: a feedback-thread reader (updateStationLabels) that saw null would derive the index
        // itself against the reducer this method is replacing (SA-C1).  A reader mid-rebuild sees the OLD index -
        // immutable, built from the old reducer - until the new one is published in one assignment at the end.
        graph = new TileGraph(pages, store.getExcludedPages());

        store.applyTo(graph);

        // AND THE SETUP SAYS WHAT THE GRAPH RUNS (OB-351): a square brought to its arrows is written back - and each square
        // of a run brought to one direction (OB-361)
        Map<TileKey, Map<RouteId, Direction>> brought = graph.getNormalizedToArms();

        for (Map<TileKey, Map<RouteId, Direction>> run : graph.getNormalizedRuns().values()) brought.putAll(run);

        for (Map.Entry<TileKey, Map<RouteId, Direction>> square : brought.entrySet())
        {
            for (Map.Entry<RouteId, Direction> road : square.getValue().entrySet())
            {
                store.setTileDirection(square.getKey(), road.getKey(),
                    road.getValue() == graph.defaultDirection(square.getKey(), road.getKey()) ? null : road.getValue());
            }
        }

        // AND SAID, AND THE SETUP UNSAVED, WHICHEVER DOOR BROUGHT THEM IN (OB-351): `open` saves what it brings and says it
        // when it returns; a bundle import or any other write into the store reaches here, so the squares wait to be
        // handed over (`takeSquaresBroughtToTheirArrows`) and the setup is marked changed
        if (!brought.isEmpty())
        {
            broughtToTheirArrows.putAll(sentencesForWhatWasBrought());

            dirty = true;
        }

        graph.validatePortals();

        // HANDED OUT ONLY ONCE REDUCED (OB-298, ADA-C2): a build asking for the reducer mid-reduce would walk lists still
        // being filled.  A build takes the field once, so it holds the old railway or the whole new one.  This session's
        // own methods read the field at every use and `graph` is published first, so they rely on every rebuild running
        // on the event thread, as every caller does (ADA2-C6).
        GraphReducer fresh = new GraphReducer(graph, store.asAuthored());

        fresh.reduce();

        reducer = fresh;

        // Now, on this thread, rather than on whichever thread happens to ask first
        deriveStationIndex();

        // AND THE TRACK NO STATION IS ON (OB-353), once a build rather than once a square painted
        stationless = squaresNoStationReaches(graph);

        // AND EACH SQUARE'S RUN, once a build (OB-361)
        leadersNow = runLeaders();
    }

    /** The squares of track no station can be reached along, as the latest build found them (OB-353). */
    private volatile Set<TileKey> stationless = Collections.emptySet();

    /** Every square of a run, to the square that speaks for it, as the latest build found them (OB-361). */
    private volatile Map<TileKey, TileKey> leadersNow = Collections.emptyMap();

    /**
     * Whether a square is track that reaches no station, however its switches lie and whichever way it runs (OB-353):
     * no train is ever sent there, so its directions are not drawn and it is greyed in the editor.  Adam, on OB-353:
     * *"make it so that arrows are hidden from track segments not connected (naively) to any station."*  Every other edit
     * still works there.
     *
     * @param tile a square
     * @return true where no station is on its track; false for one that has, and for every square while the setup has
     *         no station at all
     */
    public boolean reachesNoStation(TileKey tile)
    {
        return tile != null && stationless.contains(tile);
    }

    /**
     * The squares of each piece of track with no station on it: the squares joined to one another by their roads - every
     * road of a switch, both ways, and a link to its partner - where none of them is a station.  None while no square is
     * a station, as a setup just begun has none and its whole railway would grey.
     *
     * @param built the graph of this build
     * @return the squares, never null
     */
    private Set<TileKey> squaresNoStationReaches(TileGraph built)
    {
        Map<TileKey, Set<TileKey>> joined = new LinkedHashMap<>();

        boolean anyStation = false;

        for (TileKey tile : built.getTiles().keySet())
        {
            Map<RouteId, Route> roads = built.getRoutes(tile);

            if (roads.isEmpty()) continue;

            anyStation |= store.isStation(tile);

            Set<Side> sides = new LinkedHashSet<>();

            for (Route road : roads.values())
            {
                sides.add(road.getA());
                sides.add(road.getB());
            }

            // A link's own way through, to its partner, is the side no grid square has
            sides.add(null);

            for (Side side : sides)
            {
                Landing landing = built.landing(tile, side);

                if (landing == null || landing.getTile().equals(tile)) continue;

                // Both ways, so the pieces do not depend on which end the walk starts from
                joined.computeIfAbsent(tile, k -> new LinkedHashSet<>()).add(landing.getTile());
                joined.computeIfAbsent(landing.getTile(), k -> new LinkedHashSet<>()).add(tile);
            }

            joined.computeIfAbsent(tile, k -> new LinkedHashSet<>());
        }

        if (!anyStation) return Collections.emptySet();

        Set<TileKey> out = new java.util.HashSet<>();
        Set<TileKey> seen = new java.util.HashSet<>();

        for (TileKey start : joined.keySet())
        {
            if (!seen.add(start)) continue;

            List<TileKey> piece = new ArrayList<>();
            java.util.ArrayDeque<TileKey> next = new java.util.ArrayDeque<>();

            next.add(start);

            boolean station = false;

            while (!next.isEmpty())
            {
                TileKey at = next.poll();

                piece.add(at);

                station |= store.isStation(at);

                for (TileKey beside : joined.getOrDefault(at, Collections.<TileKey>emptySet()))
                {
                    if (seen.add(beside)) next.add(beside);
                }
            }

            if (!station) out.addAll(piece);
        }

        return Collections.unmodifiableSet(out);
    }

    /**
     * Every Point name a build of this session has emitted, and the square it stood for (RSA3-B1) - so a timetable leg
     * naming one the latest build no longer has can be carried to the copy of that square it now is.
     */
    private final Map<String, TileKey> squareOfNameSeen = new java.util.HashMap<>();

    /** And the square's own name as that build gave it, which the rest of the Point's name follows. */
    private final Map<String, String> baseOfNameSeen = new java.util.HashMap<>();

    /** And which way a train standing on it faced, where that build said (RSA4-A1). */
    private final Map<String, Side> facingOfNameSeen = new java.util.HashMap<>();

    /** The latest build's Point names, to their squares (RSA4-A1). */
    private Map<String, TileKey> squareOfNameNow = Collections.emptyMap();

    /** Its squares, to their own names. */
    private Map<TileKey, String> nameOfSquareNow = Collections.emptyMap();

    /** Its Points, to the way a train standing on each faces. */
    private Map<String, Side> facingOfNameNow = Collections.emptyMap();

    /** Its turning copies. */
    private Set<String> turningNow = Collections.emptySet();

    /** Its Points, to their squares' own names - the names a leg carried through it follows (OB-343). */
    private Map<String, String> baseOfNameNow = Collections.emptyMap();

    /**
     * The names of the edit's last build that Cancel put in the memory for the carry (OB-343), and what it put: forgotten
     * when the edit session ends or the next begins, so that the next edit's opening memory does not hold them.
     */
    private final Map<String, TileKey> keptSquares = new java.util.HashMap<>();

    private final Map<String, String> keptBases = new java.util.HashMap<>();

    /**
     * The name the latest build gives a Point of the running layout (RSA4-A1), found by the square it was a copy of and
     * the way a train on it faced (RSA5-A1): the name itself where the build still gives it to that square; otherwise the
     * same copy under the square's name now, or, where that copy is gone, the copy of the square facing the same way, a
     * turning copy last.  A copy is its facing, so no other is offered; where none faces that way, or the square is
     * gone, there is none.  Found by the square and not the name, because a station renamed to the name another has
     * hands that name to the other square.
     *
     * Where the trains stand after a run lives on the running layout alone, and a rebuild from the setup puts each back
     * by the name of the Point it stood on (OB-183), so that name has to follow the square.
     *
     */
    public String pointNamedNow(String name, String square, String facing)
    {
        TileKey at = square == null ? null : AutonomyCompanionStore.parseTileKey(square);

        // A RAILWAY THAT DOES NOT SAY WHICH SQUARE: by the name alone
        if (at == null) return pointNamedByName(name);

        if (name == null) return null;

        // THE SAME POINT, where the rebuild still gives this square the name
        if (at.equals(squareOfNameNow.get(name))) return name;

        String own = nameOfSquareNow.get(at);

        // THE SQUARE GONE: no Point to go back on - and not the name, which may be another square's now (RSA5-A1)
        if (own == null) return null;

        // THE SAME COPY under the square's name now: what follows a square's name is its copy's heading
        String base = baseOfNameSeen.get(name);

        if (base != null && name.startsWith(base))
        {
            String same = own + name.substring(base.length());

            if (at.equals(squareOfNameNow.get(same))) return same;
        }

        // THE COPY FACING THE SAME WAY, where that copy is gone - a turning copy last
        Side way = sideNamed(facing);

        if (way == null) way = facingOfNameSeen.get(name);

        String facingSo = null;
        String only = null;
        int copies = 0;

        for (Map.Entry<String, TileKey> copy : squareOfNameNow.entrySet())
        {
            if (!at.equals(copy.getValue())) continue;

            copies++;
            only = copy.getKey();

            if (way == null || way != facingOfNameNow.get(copy.getKey())) continue;

            if (facingSo == null || turningNow.contains(facingSo) && !turningNow.contains(copy.getKey()))
            {
                facingSo = copy.getKey();
            }
        }

        if (facingSo != null) return facingSo;

        // A SQUARE OF ONE COPY THAT RECORDS NO FACING: that copy - a square of one Point is one place
        if (copies == 1 && facingOfNameNow.get(only) == null) return only;

        // NO COPY FACES ITS WAY: none, rather than one facing the other way - the copy is the direction (RSA5-A2).  A
        // direction that does this to a train of the railway's writes it into the setup where it stands, and the check
        // reports it as an error: `recordTrainsTheRailwayCannotHold`.
        return null;
    }

    /**
     * The side of that name, or null.
     */
    private static Side sideNamed(String name)
    {
        for (Side side : Side.values())
        {
            if (side.name().equals(name)) return side;
        }

        return null;
    }

    /**
     * The same by the name alone, for a railway that does not say which square each Point is a copy of.
     */
    private String pointNamedByName(String name)
    {
        if (name == null || squareOfNameNow.containsKey(name)) return name;

        TileKey square = squareOfNameSeen.get(name);
        String base = baseOfNameSeen.get(name);
        String own = square == null ? null : nameOfSquareNow.get(square);

        if (own == null || base == null || !name.startsWith(base)) return name;

        // THE SAME COPY under the square's name now
        String same = own + name.substring(base.length());

        if (square.equals(squareOfNameNow.get(same))) return same;

        // THE COPY FACING THE SAME WAY, where that copy is gone
        Side facing = facingOfNameSeen.get(name);

        String facingSo = null;

        for (Map.Entry<String, TileKey> copy : squareOfNameNow.entrySet())
        {
            if (facing == null || !square.equals(copy.getValue()) || facing != facingOfNameNow.get(copy.getKey())) continue;

            if (facingSo == null || turningNow.contains(facingSo) && !turningNow.contains(copy.getKey()))
            {
                facingSo = copy.getKey();
            }
        }

        return facingSo != null ? facingSo : name;
    }

    /**
     * Carries the names every configuration stores - its captured timetable, and the road each standing train came in on
     * (RSA4-C4) - to the names this build gives its Points (RSA3-B1): the configuration in use by its build, every other
     * across a square renamed (RSA4-B1).
     *
     * A Point is named after its square (the square's name, and a heading where the square is more than one copy), so a
     * station renamed, or a square given more copies or fewer, leaves every leg through it naming a Point no longer built,
     * and the load would drop the entry.  Each such name is traced to its square - by the builds this session has seen, or,
     * for a name written before this session, as that square's own name with or without a heading - and then to a copy of
     * it: the same copy under the square's name now, or where that copy is gone, the one copy every leg through it is an
     * edge from or to.  A name that cannot be traced is left as it is.
     *
     * Every other configuration is carried across a renamed square alone: its own build can differ from this one, so any
     * other missing name is left for that build to carry when the configuration is next in use.
     */
    private void carryTheNamesAcross(AutonomyBuilder naming)
    {
        if (store == null) return;

        Map<String, TileKey> tiles = naming.tilesByName();
        Map<TileKey, String> bases = naming.uniqueNames();

        try
        {
            String active = store.getActiveConfiguration();

            for (String name : store.getConfigurationNames())
            {
                org.json.JSONObject configuration = store.getConfiguration(name);

                if (configuration == null) continue;

                List<NamedLeg> legs = legsOf(configuration);

                if (name.equals(active) ? carry(legs, tiles, bases, naming.edgesByName().keySet())
                    : rename(legs, tiles, bases))
                {
                    dirty = true;
                }

                // AND THE LEGS OF THE TIMETABLE THE EDIT SESSION'S FOLDS TOOK FROM THE RAILWAY (OB-343), which Cancel writes
                // back: carried through each build of the edit, each one a single square's, so that they name the latest
                // build's Points when Cancel comes.
                org.json.JSONObject taken = takenFromTheRailway == null ? null : takenFromTheRailway.get(name);

                if (taken != null && taken.has("timetable"))
                {
                    List<NamedLeg> takenLegs = legsOf(new org.json.JSONObject().put("globals", taken));

                    if (name.equals(active)) carry(takenLegs, tiles, bases, naming.edgesByName().keySet());
                    else rename(takenLegs, tiles, bases);
                }
            }
        }
        finally
        {
            // REMEMBERED for the next build to carry from, whatever became of this one
            squareOfNameSeen.putAll(tiles);
            baseOfNameSeen.putAll(naming.baseNames());

            Map<String, Side> facings = naming.facingByName();

            facingOfNameSeen.putAll(facings);

            // AND THIS BUILD'S, for the trains a rebuild puts back (RSA4-A1)
            squareOfNameNow = tiles;
            baseOfNameNow = naming.baseNames();
            nameOfSquareNow = bases;
            facingOfNameNow = facings;
            turningNow = naming.turningNames();
        }
    }

    /** A pair of Point names a configuration stores: a timetable leg's two ends, or one step of a train's road. */
    private static final class NamedLeg
    {
        private final org.json.JSONObject leg;
        private final org.json.JSONArray step;

        NamedLeg(org.json.JSONObject leg, org.json.JSONArray step)
        {
            this.leg = leg;
            this.step = step;
        }

        /** @param end 0 for its start, 1 for its end */
        String get(int end)
        {
            return leg != null ? leg.optString(end == 0 ? "start" : "end") : step.optString(end);
        }

        void set(int end, String name)
        {
            if (leg != null) leg.put(end == 0 ? "start" : "end", name);
            else step.put(end, name);
        }
    }

    /**
     * Every pair of Point names a configuration stores: its timetable's legs, and the steps of the road each standing
     * train came in on (RSA4-C4) - which a load drops whole where one names a Point it does not build, so the tail fell
     * back to stopping at the first fork.
     */
    private static List<NamedLeg> legsOf(org.json.JSONObject configuration)
    {
        return new ArrayList<>(keyedLegsOf(configuration).values());
    }

    /**
     * The same, each by where it is (RSA9-C1): "t" and an entry and leg of the timetable, or "r" and a square and step of
     * a road.
     */
    private static Map<String, NamedLeg> keyedLegsOf(org.json.JSONObject configuration)
    {
        Map<String, NamedLeg> legs = new LinkedHashMap<>();

        org.json.JSONObject globals = configuration.optJSONObject("globals");

        org.json.JSONArray table = globals == null ? null : globals.optJSONArray("timetable");

        for (int i = 0; table != null && i < table.length(); i++)
        {
            org.json.JSONObject entry = table.optJSONObject(i);

            org.json.JSONArray path = entry == null ? null : entry.optJSONArray("path");

            for (int j = 0; path != null && j < path.length(); j++)
            {
                org.json.JSONObject leg = path.optJSONObject(j);

                if (leg != null && leg.has("start") && leg.has("end")) legs.put("t" + i + "." + j, new NamedLeg(leg, null));
            }
        }

        org.json.JSONObject points = configuration.optJSONObject("points");

        for (String id : points == null ? Collections.<String>emptySet() : points.keySet())
        {
            org.json.JSONObject point = points.optJSONObject(id);

            org.json.JSONArray road = point == null ? null : point.optJSONArray("arrivedAlong");

            for (int j = 0; road != null && j < road.length(); j++)
            {
                org.json.JSONArray step = road.optJSONArray(j);

                if (step != null && step.length() == 2 && step.opt(0) instanceof String && step.opt(1) instanceof String)
                {
                    legs.put("r" + id + "." + j, new NamedLeg(null, step));
                }
            }
        }

        return legs;
    }

    /** How many of these are the timetable's legs. */
    private static int timetableLegs(java.util.Set<String> keys)
    {
        int legs = 0;

        for (String key : keys)
        {
            if (key.startsWith("t")) legs++;
        }

        return legs;
    }

    /**
     * The square a name written before this session stands for: the square whose own name it is, whole or with a
     * heading - the longest that fits.
     *
     * @return the square and its name, or null
     */
    private static Map.Entry<TileKey, String> squareCalled(String name, Map<TileKey, String> bases)
    {
        Map.Entry<TileKey, String> best = null;

        for (Map.Entry<TileKey, String> own : bases.entrySet())
        {
            String called = own.getValue();

            if ((name.equals(called) || name.startsWith(called + " ("))
                && (best == null || called.length() > best.getValue().length()))
            {
                best = own;
            }
        }

        return best;
    }

    /**
     * Another configuration's names carried across a renamed square (RSA4-B1), in place: a name this build lacks, whose
     * square is now called something else, to the same heading under the new name.  A name no build of this session gave
     * is remembered by the square whose name it begins with, so that a rename later can carry it.
     *
     * @return whether any name was changed
     */
    private boolean rename(List<NamedLeg> legs, Map<String, TileKey> tiles, Map<TileKey, String> bases)
    {
        boolean changed = false;

        for (NamedLeg leg : legs)
        {
            for (int end = 0; end < 2; end++)
            {
                String name = leg.get(end);

                TileKey square = squareOfNameSeen.get(name);
                String base = baseOfNameSeen.get(name);

                // STILL BUILT, ON THE SQUARE IT NAMED - or on one no build of this session placed it (RSA5-A1)
                if (tiles.containsKey(name) && (square == null || square.equals(tiles.get(name)))) continue;

                if (square == null)
                {
                    Map.Entry<TileKey, String> called = squareCalled(name, bases);

                    if (called != null)
                    {
                        squareOfNameSeen.put(name, called.getKey());
                        baseOfNameSeen.put(name, called.getValue());
                    }

                    continue;
                }

                String own = bases.get(square);

                if (own == null || base == null || own.equals(base) || !name.startsWith(base)) continue;

                leg.set(end, own + name.substring(base.length()));

                changed = true;
            }
        }

        return changed;
    }

    /**
     * The names of the configuration in use carried to this build's names, in place.
     *
     * @param legs its stored pairs of names
     * @param tiles this build's Point names, to their squares
     * @param bases this build's squares, to their own names
     * @param edges this build's edges, as "start -> end"
     * @return whether any name was changed
     */
    private boolean carry(List<NamedLeg> legs, Map<String, TileKey> tiles, Map<TileKey, String> bases,
        java.util.Set<String> edges)
    {
        // EVERY NAME THIS BUILD DOES NOT HAVE, OR GIVES ANOTHER SQUARE, traced to its square
        Map<String, TileKey> stale = new LinkedHashMap<>();
        Map<String, String> carried = new LinkedHashMap<>();

        for (NamedLeg leg : legs)
        {
            for (String name : new String[] {leg.get(0), leg.get(1)})
            {
                if (stale.containsKey(name)) continue;

                TileKey square = squareOfNameSeen.get(name);
                String base = baseOfNameSeen.get(name);

                // STILL BUILT, ON THE SQUARE IT NAMED (RSA5-A1).  A station renamed to the name another has hands that
                // name to the renamed square, and a name the build still gives was left - naming the renamed station.
                // Seen by the build before this one, which is this carry's: the memory is written after it.
                if (tiles.containsKey(name) && (square == null || square.equals(tiles.get(name)))) continue;

                // WRITTEN BEFORE THIS SESSION: the square's own name, whole or with a heading - the longest that fits
                if (square == null)
                {
                    Map.Entry<TileKey, String> called = squareCalled(name, bases);

                    if (called != null)
                    {
                        square = called.getKey();
                        base = called.getValue();
                    }
                }

                if (square == null || base == null || !bases.containsKey(square) || !name.startsWith(base)) continue;

                stale.put(name, square);

                // THE SAME COPY under the square's name now: what follows the name is the copy's heading
                String same = bases.get(square) + name.substring(base.length());

                if (square.equals(tiles.get(same))) carried.put(name, same);
            }
        }

        // WHERE THAT COPY IS GONE - a square split, or one copy fewer - the one copy every leg through it runs along
        for (Map.Entry<String, TileKey> each : stale.entrySet())
        {
            if (carried.containsKey(each.getKey())) continue;

            String fits = null;
            int fitting = 0;

            for (Map.Entry<String, TileKey> copy : tiles.entrySet())
            {
                if (!copy.getValue().equals(each.getValue())) continue;

                boolean every = true;

                for (NamedLeg leg : legs)
                {
                    String start = leg.get(0);
                    String end = leg.get(1);

                    if (!start.equals(each.getKey()) && !end.equals(each.getKey())) continue;

                    String from = start.equals(each.getKey()) ? copy.getKey() : carried.getOrDefault(start, start);
                    String to = end.equals(each.getKey()) ? copy.getKey() : carried.getOrDefault(end, end);

                    if (!edges.contains(from + " -> " + to)) every = false;
                }

                if (every)
                {
                    fits = copy.getKey();
                    fitting++;
                }
            }

            if (fitting == 1) carried.put(each.getKey(), fits);
        }

        boolean changed = false;

        for (NamedLeg leg : legs)
        {
            for (int end = 0; end < 2; end++)
            {
                String now = carried.get(leg.get(end));

                if (now != null)
                {
                    leg.set(end, now);

                    changed = true;
                }
            }
        }

        return changed;
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
         * corrected in the autonomy editor and by the first real run's capture.  A facing the square already recorded,
         * kept for the train, is counted too: it is no more evidence about the train than a chosen one (OB-304).
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
         * operator (Adam: *"Carry the old file's directions onto the diagram - yes, to the extent
         * possible."*), against the way each now runs.
         */
        public final Map<TileGraph.DirectionKey, Direction> directionsCarried = new LinkedHashMap<>();

        /**
         * The pieces of track the old file ran one way and this diagram does not, left as the diagram has them because
         * its directions have already been set.
         */
        public int directionsNotCarried = 0;
        /**
         * Names written onto a square that had none.
         */
        public int matched = 0;

        /**
         * Squares left alone because they had a name before this import - each counted once, however many old points stand
         * on it (RSA44-C3, RSA45-C1).
         */
        public int skipped = 0;

        /**
         * Old points whose square this import had already met from another point on the same sensor - a station and its
         * approach guard, the ordinary shape of an old file - each as a line naming the point and the name its square kept,
         * whether this import named the square or it had its name before (RSA44-C3, RSA45-C1).  A square has one name.
         */
        public final List<String> namesNotKept = new ArrayList<>();

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
         * Not placed: the running model refuses a placement naming a locomotive it cannot find by invalidating the WHOLE
         * layout, which would leave a setup that will not load, with nothing to connect it to the import.
         */
        public final List<String> unknownLocomotives = new ArrayList<>();

        /**
         * The same, with the square each would have stood on, for the list the import door shows (OB-349).
         */
        public final List<LocomotiveLeftOut> unknownPlacements = new ArrayList<>();

        /**
         * Locomotives the file places at more than one point.  Only the first is placed.
         *
         * The running model invalidates on this too - one locomotive cannot stand in two places - and
         * an old graph that has drifted can easily name the same one twice.
         */
        public final List<String> duplicateLocomotives = new ArrayList<>();

        /**
         * Locomotives the file places that the configuration imported into already has standing somewhere.  Not placed
         * again: the train stays where the configuration has it (RLA-B2).
         */
        public final List<String> alreadyPlaced = new ArrayList<>();

        /**
         * Locomotives the file gives a home that the configuration imported into already has a home for.  That home is
         * kept, and the message names them (RLA2-C3).
         */
        public final List<String> homesKept = new ArrayList<>();

        /**
         * Locomotives the file places on a square this configuration already has another train standing on.  Not
         * placed: one square holds one train, and the one there stays (OB-304).
         */
        public final List<String> squareTaken = new ArrayList<>();

        /**
         * Locomotives the file gives a home on a square that is already another train's home.  Not homed there: a
         * square is one train's home, and the one it has stays (OB-304).
         */
        public final List<String> homeSquareTaken = new ArrayList<>();

        /**
         * Locomotives the file places that were not placed because the configuration imported into is the one running:
         * where a train stands there is the railway's to say (RLD3-C1).
         */
        public final List<String> notPlacedInUse = new ArrayList<>();
    }

    /**
     * The per-point settings an old graph holds that the build still reads, unchanged.
     *
     * Copied into the configuration verbatim and emitted verbatim: the builder passes unknown extras straight through.
     * Listed rather than copied wholesale because the rest of a legacy point - name, station, s88, terminus, reversing,
     * x, y - is handled deliberately or derived now, and copying those would fight the derivation.
     *
     *   priority          how strongly autonomy prefers this destination
     *   speedMultiplier   the pace trains take through it
     *   excludedLocs      the locomotives this station will not accept
     *   active            whether the railway may use the square at all.  Carried as given, except on a
     *                     station, where `false` arrives as "on, and not a destination autonomy chooses"
     *                     (REG-B1, below).
     *
     * `active` is carried unfiltered on a non-station because it is the operator's setting and the import is a
     * migration, not an edit (D24-B5).  An `active: false` point is one no path may pass through OR END ON, manual
     * routes included.  On a station that would shut out a hand-picked route that v2.8.1 allowed - there `active: false`
     * was the old way of saying "autonomy stays out, I drive in by hand" - so (Adam: *"yes, translate as on
     * but not auto destination."*) on a station `active: false` is written as `autoDestination: false` and the switch
     * is left on (REG-B1).
     */
    static final List<String> CARRIED_SETTINGS =
        Arrays.asList("priority", "speedMultiplier", "excludedLocs", "active", "maxTrainLength");

    /**
     * Brings station names, station flags and lengths across from a legacy autonomy.json.
     *
     * The old graph held its points by NAME and carried the s88 each one watched.  The diagram derives its points from
     * the feedback squares, so the two are matched by that s88.  What a square is CALLED, and which squares are
     * stations, are decisions somebody made once and would otherwise make again, square by square, on upgrading.
     *
     * Names already here are kept, the same rule importing a configuration follows: this fills gaps, it does not
     * overwrite somebody's work with a file's.
     *
     * Nothing is written to disk BY THIS METHOD.  Its caller, `AutonomyViewerPanel.importLegacyGraph`, reads the rest of
     * the file and saves once it has, or puts the setup back as it was where the file fails part way (RLA-C3), so an
     * import is committed only whole (ACC-C5).
     *
     * @param legacy the parsed autonomy.json
     * @return what was matched, skipped and not found
     */
    public LegacyImport importLegacy(org.json.JSONObject legacy)
    {
        return importLegacy(legacy, null);
    }

    /**
     * @param legacy the parsed autonomy.json
     * @param knownLocomotives the names the locomotive database holds, or null not to check
     * @return what was matched, placed, marked, carried and refused
     */
    public LegacyImport importLegacy(org.json.JSONObject legacy, Set<String> knownLocomotives)
    {
        return importLegacy(legacy, knownLocomotives, true);
    }

    /**
     * The same, told whether the file's placements are taken - not into the configuration running, where a train stands
     * is the railway's to say (RLD3-C1); the rest of the file fills gaps as ever.
     *
     * @param legacy the parsed autonomy.json
     * @param knownLocomotives the names the locomotive database holds, or null not to check
     * @param placeTrains whether the file's placements are written
     * @return what was matched, placed, marked, carried and refused
     */
    public LegacyImport importLegacy(org.json.JSONObject legacy, Set<String> knownLocomotives, boolean placeTrains)
    {
        return sessionImport.importLegacy(legacy, knownLocomotives, placeTrains);
    }

    /**
     * The address in a legacy command's accessory name, when that accessory is a signal (RG4-B1).
     *
     * A 2.8.1 command names its accessory as a display string - "Signal 116", "Switch 68".  Only signals are wanted: a
     * switch position is reproduced from the track diagram, and a signal is driven only where the operator pairs it.
     * Anything that is not a signal, or whose number does not parse, is not a signal address: a report that throws on a
     * malformed old file is worse than one that leaves a line out.
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

        // THE DIGITS, AND WHATEVER THE PROTOCOL ADDS AFTER THEM (VLD-B1): the standardized name is
        // `Accessory.getNameWithProtocol`, which for any decoder but the default appends the protocol ("Signal 116 DCC"),
        // so parsing the whole remainder as a number would find no signals on a DCC railway.
        int end = 0;

        while (end < rest.length() && Character.isDigit(rest.charAt(end))) end++;

        if (end == 0) return null;

        // DIGITS ONLY, so a sign is not an address (VLD-C5): `Integer.valueOf` accepts "+116" and "-5", and neither is a
        // real accessory.  What may follow the digits is a protocol token and nothing else - "Signal 1 2" or
        // "Signal 116red" is not a name this program writes.
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
     * Adam: **"yes, but list them in the log and mention that in the dialog."**  Counted rather than merely
     * named, because "the timetable was not imported" and "the 36 entries of your timetable were not imported" are
     * different sentences to somebody deciding whether to go back to the old file.
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

                    // AND THE SIGNALS BY NAME, not just inside that count (RG4-B1): most commands are switch positions, which
                    // the derivation reproduces, but the new model drives a SIGNAL only where it is paired with a station,
                    // a gesture the operator has to make by address.  NAMED, NOT COMPARED: deciding which are already
                    // paired needs the reduction, and this runs on a file not yet imported.  Naming a signal already paired
                    // costs a glance at a menu; omitting one leaves a signal that lies about occupancy.
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

                // HAND-WRITTEN LOCKS, the fifth thing this drops and the only silent one (ACC-B1): a 2.8.1 edge could carry
                // `lockedges`, and the new model derives locks from geometry (`GraphReducer.deriveLocks` locks two edges
                // that occupy a shared tile).  That cannot reproduce a lock between edges sharing NO tile - parallel
                // adjacent tracks, an electrical section - so two trains may be permitted at once where the file forbade
                // it.  Counted rather than compared: a report wrong in the safe direction beats one silent about locks
                // that are not reproduced.
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

        // AND THE SIGNALS BY NAME (RG4-B1), sorted by address so the operator working down the list with the Pair Signal
        // item open does not lose their place.  Collected in the loop above, so the null and isNull guards cannot drift (VLD-C7).
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

        // AND A FILE THAT IS NOT ACTUALLY A 2.8.1 ONE (ACC-C7): `detectImportFormat` calls any file whose `points` is an
        // ARRAY a legacy graph, and a configuration serialised by `Layout.toJSON` is one too, so exporting a modern setup
        // and importing it again comes through this door.  Reported rather than refused: a key the old format never wrote
        // is evidence rather than proof, as somebody could hand-edit a legacy file.
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
     * The store's own importBundle holds authored data and knows nothing about the derivation, and a configuration
     * carries the flags that terminus, reversing and a station's switch are DERIVED from.  Here rather than in the
     * caller because it is the same invariant every time: authored data changed, so the derivation is stale.
     *
     * @param name what to call the configuration here
     * @param file the parsed export
     * @return how many shared entries were filled in
     */
    public int importBundle(String name, org.json.JSONObject file) throws java.io.IOException
    {
        return sessionImport.importBundle(name, file);
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
     * A layout whose pages draw the same track twice - an overview and a detail view of one yard, say - gives two
     * squares the same s88, so the same sensor becomes two destinations, a legacy import cannot decide which square a
     * name belongs to, and nothing downstream can tell which one a train is standing on.
     *
     * Earliest page wins, in the order the layout lists them, because that is the one a reader thinks of as the real one
     * and the only rule that does not depend on which page happens to be open.  A page that is shut does NOT contribute
     * its sensors to what counts as seen.
     *
     * TWO CALLERS, AND THAT IS SETTLED (OB-130): a new configuration runs this because a starting point is wanted; a
     * legacy import runs it because a setup built before this rule existed has never been held to it.  The second may
     * shut pages the user had chosen to keep (Adam: "yes, it may override."): a setting made in ignorance of
     * a rule is not a decision the rule has to honour.  It remains a starting point rather than a policy everywhere
     * else: the page checkboxes are still there.
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

            // AND A PAGE THE OPERATOR TURNED BACK ON IS LEFT ALONE (TST-B15): this method's second caller
            // is a legacy import into an EXISTING setup, so a page it had shut and the operator had switched back on must
            // not look like a page nobody had considered.  The "may override" ruling holds for pages nobody has had an
            // opinion about; an explicit re-enable is an opinion.
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
     * Stated here, as a rule rather than as a branch inside a button.  Whatever is already chosen wins: importing a
     * configuration is not a request to switch to it, and the shared half an import merges - the names, the stations,
     * the lengths - belongs to every configuration equally, so the one already running needs re-deriving whichever was
     * imported.
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
        return SessionImport.detectImportFormat(file);
    }

    /**
     * Each copy of a square a train may be put down on, with the way it faces (OB-270, GUI-B1).
     *
     * `facingsFor` names every copy, but a copy trains may not arrive at is built as no station, and a train stood there is
     * one autonomy will not start (`autolayout.why.startFacingBarred`).  `copyFacing` asks this first, so that among the
     * copies facing one way it takes one trains may arrive at.  The placement doors keep any heading a train can leave by
     * instead (`departableFacingsFor`, OB-284).  With no running layout every copy is returned.
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
     * Adam: *"this should check for barred departure directions, not arrival ones.  for barred arrival
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
     * have come to rest facing so.  A copy trains may arrive at comes first, because the first copy facing a way can be
     * one no train may arrive at (the plain copy may be no station while only the turning twin is).
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
     * The copy of a square a train stands on where nothing says which way it faces: the build's own choice for a
     * placement with no facing (`AutonomyBuilder.startableCopy`), read off the running layout - a copy trains may arrive
     * at, the plain one first, else the first copy.
     *
     * Where no copy of a station can leave, the doors that put a train down stand it here and record no facing, as the
     * editor's Place does through the build (OB-350).
     *
     * @param square the square
     * @param running the running layout, or null
     * @return that copy, or null when the railway has no Point on the square
     */
    public org.traincontrol.automation.Point copyWithNoFacing(TileKey square, org.traincontrol.automation.Layout running)
    {
        if (square == null || running == null || getStationIndex() == null) return null;

        List<org.traincontrol.automation.Point> all = getStationIndex().pointsAt(running, square);

        for (org.traincontrol.automation.Point copy : all)
        {
            if (copy.isDestination() && !copy.isTerminus() && !copy.isReversing()) return copy;
        }

        for (org.traincontrol.automation.Point copy : all)
        {
            if (copy.isDestination()) return copy;
        }

        return all.isEmpty() ? null : all.get(0);
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
     * A square is several Points once it is split - one per facing a train can hold there - and the copy a locomotive is
     * on is what the runtime uses to decide where it can go next.  `facingsFor` maps each copy's NAME to the side its
     * train faces, the same map `captureFromLayout` reads in the other direction.
     *
     * **Not `moveLocomotive`**, the placement gesture: it refuses while anything is running, requires the target to be a
     * destination (which a split copy need not be) and adds the locomotive to the run list.  This locomotive is already
     * placed; what changes is which copy of one square it stands on.
     *
     * Quiet about everything it cannot do (no layout, no copies, an unknown locomotive): the setup write stands on its
     * own.
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

        // A COPY FACING THAT WAY, one trains may arrive at first (GUI-B1): the first copy facing a way can be one no train
        // may arrive at, which autonomy will not start from.  But never a copy facing another way (TDY2-A1): where only a
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

        // NOT A TRAIN UNDER WAY (RLV11-C3, RLV12-C4).  A locked path records its train on every point of it, its start
        // among them, so a train under way reads as standing on each square it will pass - and standing it on another copy
        // sweeps it off the rest of its path while it runs.  Here, so that every caller has it: the turn written after an
        // arrival, the direction follow and the facing menu.
        if (running.isAlreadyUnderway(train)) return;

        if (onto.getCurrentLocomotive() == train) return;

        // ALREADY ON A COPY THAT FACES THAT WAY, AND ONE TRAINS MAY ARRIVE AT: left there (RSA52-B1).  Moved onto the other
        // side's plain copy at the next idle moment, a train a plan turned on a may-turn station's turning copy would lose
        // its place for the next entry of a recorded timetable.  `Layout.copyAfterTheTurn` asks the same.
        if (from != null && facing == facingsFor(tile).get(from.getName()) && from.isDestination()) return;

        // AND THE TAIL COMES WITH IT (REV9-B1).  `Point.setLocomotive` clears `arrivedFrom` on every change of occupant,
        // which is wrong for THIS move, the same train re-stood on a sibling copy of one square: behaviour.md 4 - *"turning
        // the train round at the platform does not move its tail: the carriages stay where they stopped"*.  Saved and put
        // back, the way `TrainControlUI.putTheTrainsBack` does, and read from the copy it was ON, because that is the one
        // the arrival wrote it to.
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

        // Put back unconditionally: `setLocomotive` just above cleared both fields on this copy, so there is never anything
        // newer to keep (TDR-B2).
        if (tail != null) onto.setArrivedFrom(tail);

        if (along != null) onto.setArrivedAlong(along);

        // AND THE RAILWAY'S WATCHERS TOLD, as `moveLocomotive` tells them (MT-759): the diagram draws a standing train's
        // icon from the copy it is on and redraws only when the railway says something moved (Adam: "make sure the icon
        // matches the label").
        running.refreshUI();
    }

    /**
     * Faces a train the way it came in, because the railway has just turned it round there (REV9-A1).
     *
     * **The answer is absolute.**  `flipFacing` is relative - it writes the other choice of the facing it finds - and what
     * it finds cannot be trusted when `TrainControlUI.reconcileFacingWhenIdle` runs (behaviour.md 6a: *"a run moves trains,
     * where they ended up lives only in the running layout, and nothing writes it back to the setup when the run ends"*;
     * OB-190).  What IS reliable is the side the train came in by.  behaviour.md 4: *"a train faces the way it will leave;
     * once it has been turned round the two point the same way while the carriages have not moved."*  So the facing of a
     * train that has just turned at its destination is its `arrivedFrom`, which the arrival wrote onto this very Point;
     * applying it a second time writes the same thing again rather than undoing it.
     *
     * **Both records, as `flipFacing` does** (DIR-B3; Adam: *"flipFacing should also update the running
     * layout"*): which copy a train stands on IS its direction.  **Quiet where it cannot answer, by returning null** - a
     * Point that is not this square's, a square the build has no copies for, an arrival side no copy can face; the caller
     * keeps the record, so the turn is retried on the next refresh.
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

        // AND NOT UNDER WAY AGAIN (RLV11-C3).  A train's locked path records it on its start - where it turned - so one that
        // turned and was sent off again read as standing there, and standing it on another copy swept it off the rest of
        // its path while it ran.  It owes nothing now: its next arrival writes the record again or clears it.
        if (running != null && running.isAlreadyUnderway(turnedAt.getCurrentLocomotive())) return null;

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
     * Turns a placed locomotive round on the graph, because the railway turned it.
     *
     * Adam: **"changing the direction on the graph itself does not emit a locomotive direction command.  But a locomotive
     * direction command WILL update the direction on the graph if it does not match."**  So one way only: the decoder is
     * what the railway actually does, the setup is a record of it, and `setFacing` writes nothing to the track.
     *
     * **The caller decides that something changed**, not this method: nothing maps a facing (a Side) to a direction
     * (forwards or backwards).  What IS knowable is that a direction command reverses whatever was true before, so the
     * window watches for the change and this performs the consequence; nothing is stored, so no migration.
     *
     * **What flipping means on a curve**: not the compass opposite.  A train on a curve joining north to east faces one of
     * those two, and turning it round makes it face the other - "the other choice" of `facingChoices`.  **Left alone where
     * the answer is not obvious** (a locomotive that is not placed, a square where neither the railway nor the setup says
     * which way it faces, a square offering other than two facings): reported by returning null.
     *
     * **AND THE RUNNING LAYOUT** (`DIR-B3`; Adam: *"flipFacing should also update the running layout."*):
     * which copy a locomotive stands on decides where it can go next, and `captureFromLayout` derives the facing from that
     * copy, so writing only the setup left two records of one fact with no arbiter.  Both writes are here, in one method,
     * so that they cannot drift.
     *
     * @param locomotive the locomotive that has just been turned
     * @param running the layout to move it on, or null to write the setup only
     * @return the square whose facing changed, or null when nothing did
     */
    public TileKey flipFacing(String locomotive, org.traincontrol.automation.Layout running)
    {
        if (locomotive == null) return null;

        // WHERE THE TRAIN IS, THEN WHERE THE SETUP THINKS IT IS (REG6-A2): the setup names the square a train set off from
        // until the next `captureFromLayout` writes the arrival back, and a reversal made DURING a run is deferred until
        // the run ends - by then the train is at the far end, so the running layout is asked first because it knows.
        // The setup is still walked afterwards, and in full: DIR-C3 is the rule that a locomotive recorded on two squares
        // gets a follow on the square that CAN be decided.
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

        // THE SETUP ONLY WHEN THE RAILWAY DOES NOT KNOW (REG7-A2).  Appended to the running layout's answer, the setup's
        // departure square (which it names until the next `captureFromLayout`) would come up after an arrival square the
        // loop below cannot decide, and the empty platform the train left would have its facing flipped.  DIR-C3 is not
        // weakened: two SETUP squares are still walked in full when the railway has no opinion.
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
            // THE WAY IT FACES ON THE RAILWAY, where it stands there (TDY3-A2, AUT3-A1): the copy it is on IS its facing.
            // The setup's FACING is the heading it set off with until a capture writes the arrival back, so after a run it
            // is absent or another train's.  The setup answers only where the railway does not know.
            Side recorded = facingOfTrainOnTheRailway(tile, locomotive, running);

            if (recorded == null) recorded = getFacing(tile);

            List<Side> choices = facingChoices(tile);

            // CARRY ON RATHER THAN GIVE UP (DIR-C3): a locomotive recorded on TWO squares (the checks report it) must still
            // get a follow on the square that can be decided.
            if (recorded == null || choices.size() != 2) continue;

            // A RECORDED FACING THIS SQUARE CANNOT HOLD (DIR-C4), the state `OB-177` taught the menu to show: not flipped,
            // because "the other one" means nothing when the recorded value is neither of them, and not logged from here
            // (no control station); `facingsThatCannotBeHeld` reports it in the findings panel.
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
     * Read from the configuration rather than from the running layout: the configuration is what the next build reads,
     * so it is where a second placement can hide.
     *
     * PAGES THE USER SWITCHED OFF ARE LEFT OUT.  Every other check here reads from the reducer, which is built with
     * excluded pages already dropped; this one reads raw JSON, and the duplicate-locomotive finding it feeds is an ERROR
     * saying autonomy "refuses the whole setup", so it must not send the user to a page they excluded in order to fix
     * it.  A placement on an excluded page is not wrong: excluding is reversible, and switching the page back on brings
     * the placement back.
     *
     * @return square to locomotive name, empty when nothing is loaded
     */
    private Map<TileKey, String> placedLocomotives()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();

        // THROUGH THE SHARED WALK `tilesWhere` (DIR-C6, WK3-C3), so a qualification added to the walk reaches every caller.
        // The excluded-page filter stays here, because it belongs to this question.
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
     * Squares, not names: a caption points at the sensor it is about, so asking whether a station is labelled is not a
     * matching of text against text.
     *
     * All pages, including excluded ones: exclusion says autonomy will not route over a page, not that
     * the page has stopped being drawn, and a caption there is still on the user's screen.
     *
     * Stations only (GSE-C4): a caption kept for a square that is not a station now is not drawn, so it labels nothing.
     *
     * @return the stations that have a caption somewhere
     */
    public Set<TileKey> getLabelledStationTiles()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (TileKey station : store.getCaptions().values())
        {
            if (station != null && store.isStation(station)) out.add(station);
        }

        return out;
    }

    /**
     * The station a caption on this square is about.
     *
     * A caption kept for a square that is not a station now is none (GSE-C4).  Demoting a station keeps its caption,
     * ignored while the square is not a station, and this is what the diagram and the editor draw by
     * (`TrainControlUI.autonomyCaptionAt`), so it answers null until the square is a station again.  Drawn, the label
     * is registered on a plain point, and a train reversing there lights up a station name for a place that is no longer
     * one.  The store and `captionsFor` still hold it, for the moves and deletes that have to carry or forget it.
     *
     * @param captionTile the square the text sits on
     * @return the sensor's square, or null when nothing is captioned there or the sensor is not a station now
     */
    public TileKey getCaptionTarget(TileKey captionTile)
    {
        TileKey station = store.getCaptionTarget(captionTile);

        return station != null && store.isStation(station) ? station : null;
    }

    /**
     * Every caption this station has: drawn, or kept while the square is not a station (GSE-C4) - `getCaptionTarget` is
     * the one that says what is drawn.
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
     * Whether the diagram draws the caption on this square: the one rule for it, asked by the drawing and by the setup
     * check alike (MT-337, OB-341).
     *
     * A station's name is drawn once PER PAGE (Adam: *"The duplicate caption check should be per page.  It is OK
     * to show on another page what is going on on another."*).  A caption beside the station is drawn; one on the station's
     * own square gives way to one beside it on the SAME page, because the two would land next to each other and say the
     * same thing twice (MT-337).  A caption on another page never makes it give way.
     *
     * **Only on a page that draws captions.**  No window draws one on a page left out of autonomy
     * (`LayoutGrid.hidesStationCaptions`), and a page this session no longer has has no grid at all.
     *
     * **And none for a station unnamed and switched off** (Adam: *"hide the label if empty and nothing can
     * pass."*): an unnamed station draws a dash, the placeholder that says it wants a name, and on a square out of service
     * there is nothing to name it FOR.  A named one keeps its name however it is switched, and an unnamed one in service
     * keeps its dash.
     *
     * Asked of the station, not of the station's Point: a sensor with no track to it, marked a station, draws its
     * captions all the same.
     *
     * @param where the square the text sits on
     * @return false where there is no caption, where it is kept for a square that is no station now (GSE-C4), where its page
     *         is left out or gone, where its station is unnamed and out of service, and where it is a station's own and
     *         gives way to another caption on its page
     */
    public boolean isCaptionDrawn(TileKey where)
    {
        TileKey station = getCaptionTarget(where);

        if (station == null || !onAPageThatDrawsCaptions(where) || isUnnamedAndSwitchedOff(station)) return false;

        if (!station.equals(where)) return true;

        for (TileKey elsewhere : captionsFor(station))
        {
            if (!station.equals(elsewhere) && station.getPage().equals(elsewhere.getPage())) return false;
        }

        return true;
    }

    /**
     * Whether a station has no name of its own and is switched off.
     *
     * @param station the sensor’s square
     * @return true for one the diagram draws no caption for
     */
    private boolean isUnnamedAndSwitchedOff(TileKey station)
    {
        if (!Boolean.FALSE.equals(getPointProperty(station, "active"))) return false;

        String named = store.getPointName(station);

        return named == null || named.trim().isEmpty();
    }

    /**
     * Whether the track diagram draws this square when trains run, which is where a caption can be seen.
     *
     * The autonomy editor shows the whole page, padding included, and puts the page object the session shares into edit
     * mode, where its bounds are the whole page; the running diagram draws only the part its COMPONENTS cover, and a
     * caption is not a component.  Worked out from the components alone, as the running grid does (AD-C6).
     *
     * @param square a square
     * @return false for a square outside that part, or on a page this session does not have
     */
    public boolean isInTheDrawnArea(TileKey square)
    {
        LayoutDiagram page = pageOf(square);

        return page != null && page.isDrawnWhenRunning(square.getX(), square.getY());
    }

    /**
     * Whether a page this session has and autonomy uses is the page of this square, which is where a caption can be drawn.
     *
     * @param square a square
     * @return false for a page that is gone or left out of autonomy
     */
    private boolean onAPageThatDrawsCaptions(TileKey square)
    {
        // Page objects that a rename or a delete has left behind cannot say a page is gone (`pagesStale`)
        return (pagesStale || pageOf(square) != null) && !store.getExcludedPages().contains(square.getPage());
    }

    /**
     * The squares the diagram draws a station's name on.
     *
     * @param station the sensor's square
     * @return those of its captions that `isCaptionDrawn` says are, possibly none
     */
    public List<TileKey> drawnCaptionsFor(TileKey station)
    {
        List<TileKey> drawn = new ArrayList<>();

        for (TileKey where : captionsFor(station))
        {
            if (isCaptionDrawn(where)) drawn.add(where);
        }

        return drawn;
    }

    /**
     * Shows a station's name on a square, or stops showing it.
     *
     * @param captionTile where the text goes
     * @param stationTile the sensor it is about, or null to clear the square
     */
    public void setCaption(TileKey captionTile, TileKey stationTile)
    {
        // One caption per station PER PAGE - decided HERE rather than at each door: of the three ways to caption a station
        // (automatic placement, the autonomy editor, dragging the square in the diagram editor) only the first removed the
        // old one, so choosing a new square left the station named twice.
        //
        // On the new square's page, and no other (the editor goes per page like the setup check): on a page
        // where the station has no caption this ADDS one and keeps the captions on other pages, and on the same page it
        // MOVES it, so each page shows the name at most once.  Cleared before setting, so moving a caption onto a square
        // that already shows the same station is not a clear-then-set of the same entry.
        if (stationTile != null) clearCaptions(stationTile, captionTile);

        store.setCaption(captionTile, stationTile);
        touched();
    }

    /**
     * Forgets the captions belonging to a square that has just been deleted from the diagram.
     *
     * Both directions, because a square can be either end of a caption:
     *
     *   the square the text sits ON      its caption goes with it.  An EMPTY square keeps its caption (a caption may
     *                                    sit on blank space), but a square somebody has just deleted is gone.
     *   the station the text is ABOUT    every caption naming it goes: text pointing at track that no longer exists is
     *                                    the orphan this whole design removed.
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
        // AND NOT THE NAMES A LAST EDIT'S CANCEL KEPT, which are that edit's - see `restoreSetup`
        forgetTheNamesKept();

        // AND WHAT THE CARRY REMEMBERS, as the editor's Cancel will want it (RSA9-B2) - see `asTheEditBegan`
        asTheEditBegan = remembered();

        // AND WHAT THE RAILWAY CHANGES ALONE FROM HERE, which Cancel keeps (OB-343) - see `takenFromTheRailway`
        takenFromTheRailway = new LinkedHashMap<>();

        pausesTakenFromTheRailway = new LinkedHashMap<>();

        return store.rememberBeforeEdit(store.snapshotSetup());
    }

    /**
     * The editing session ended properly, so there is nothing to undo.
     */
    public boolean endEditSession()
    {
        takenFromTheRailway = null;

        pausesTakenFromTheRailway = null;

        forgetTheNamesKept();

        return store.forgetBeforeEdit();
    }

    /**
     * What the settings folds took from the railway while an edit session was open: configuration name, to the settings it
     * took - the value, or JSON null where the railway stopped writing one - or null while no edit session is open
     * (OB-343).
     *
     * **A fold's settings are the railway's facts, not the edit's.**  The Auto tab and the cards are live with an editor open,
     * so a setting moved there and a train paused on its card change the railway alone, and the next in-editor rebuild folds
     * them into the setup.  Cancel puts back the setup the editor opened with, which has none of them, so `restoreSetup`
     * writes these over what it puts back, the railway's last word on each setting: Cancel undoes the edit and nothing else.
     */
    private java.util.Map<String, org.json.JSONObject> takenFromTheRailway;

    /**
     * The same for the paused trains, which are taken BY TRAIN: configuration name, to each train the railway's list gained
     * or lost against the record of how the railway was built, and whether it is paused (OB-343).  The railway's list holds
     * the trains the editor's own ticks paused as well as the ones a card paused, and a fold applies to the setup's list
     * only the trains the railway gained or lost against the record, which already holds the ticks
     * (`TrainControlUI.notePausesAsBuilt`) - so what is noted here is the cards' alone, the only trains Cancel may keep.
     * By name, so a locomotive renamed meanwhile is followed here (`followALocomotiveName`).
     */
    private java.util.Map<String, java.util.Map<String, Boolean>> pausesTakenFromTheRailway;

    /**
     * Follows a locomotive's new name - or its deletion, `to` null - into the trains the edit session's folds noted (OB-343).
     * The store has repaired the setup, its snapshot and the pre-edit note by then, and a name left here would be applied to
     * a list that no longer holds it (a train would come out paused after Cancel, or a deleted locomotive's name be written
     * back).
     *
     * @param from the name as it was
     * @param to the new name, or null when the locomotive was deleted
     */
    public void followALocomotiveName(String from, String to)
    {
        if (pausesTakenFromTheRailway == null || from == null) return;

        for (java.util.Map<String, Boolean> pauses : pausesTakenFromTheRailway.values())
        {
            Boolean paused = pauses.remove(from);

            if (paused != null && to != null) pauses.put(to, paused);
        }
    }

    /** Forgets the names of an edit's last build that Cancel put in the memory for the carry - see `keptSquares`. */
    private void forgetTheNamesKept()
    {
        for (Map.Entry<String, TileKey> kept : keptSquares.entrySet())
        {
            squareOfNameSeen.remove(kept.getKey(), kept.getValue());
        }

        for (Map.Entry<String, String> kept : keptBases.entrySet())
        {
            baseOfNameSeen.remove(kept.getKey(), kept.getValue());
        }

        keptSquares.clear();
        keptBases.clear();
    }

    /**
     * Writes what the settings folds took from the railway since the edit session began over the setup just put back
     * (OB-343; see `takenFromTheRailway`).
     */
    private void putBackWhatTheRailwayChangedAlone()
    {
        if (takenFromTheRailway == null) return;

        for (Map.Entry<String, java.util.Map<String, Boolean>> pauses : pausesTakenFromTheRailway.entrySet())
        {
            org.json.JSONObject configuration = store.getConfiguration(pauses.getKey());

            if (configuration == null) continue;

            org.json.JSONObject globals = configuration.optJSONObject("globals");

            if (globals == null)
            {
                globals = new org.json.JSONObject();

                configuration.put("globals", globals);
            }

            Set<String> gained = new java.util.TreeSet<>();
            Set<String> lost = new java.util.TreeSet<>();

            for (Map.Entry<String, Boolean> pause : pauses.getValue().entrySet())
            {
                if (pause.getValue()) gained.add(pause.getKey());
                else lost.add(pause.getKey());
            }

            applyPauses(globals, gained, lost);
        }

        for (Map.Entry<String, org.json.JSONObject> taken : takenFromTheRailway.entrySet())
        {
            org.json.JSONObject configuration = store.getConfiguration(taken.getKey());

            if (configuration == null) continue;

            org.json.JSONObject globals = configuration.optJSONObject("globals");

            if (globals == null)
            {
                globals = new org.json.JSONObject();

                configuration.put("globals", globals);
            }

            for (String key : taken.getValue().keySet())
            {
                Object value = taken.getValue().get(key);

                if (org.json.JSONObject.NULL.equals(value)) globals.remove(key);
                else globals.put(key, copyOfSetting(value));
            }
        }
    }

    /**
     * Whether `revertUnfinishedEdit` found a pre-edit note of the right shape that still would not read - a value of the
     * wrong type inside it - so that `unusableEditNote` says so of it, as of a note refused on its shape (GSP-C4).
     */
    private boolean editNoteWouldNotRead;

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
        return store.hasUnfinishedEditNote() && (editNoteWouldNotRead || store.unfinishedEdit() == null);
    }

    /**
     * Puts the setup back to before an editing session that never ended, if there was one.
     *
     * Called whenever a session is BUILT - which is at startup, and also on every page-set change,
     * diagram re-download, page combine and layout reload, because each of those throws the session
     * away and the next caller rebuilds it. The caller is responsible for not asking while an editor
     * is open; `TrainControlUI.getAutonomySession` says how and why.
     *
     * Almost always finds nothing: the note is left behind only when the process died with the layout editor open, and
     * then disk holds a setup keyed to squares the diagram never moved.
     *
     * @return true when something was put back
     */
    public boolean revertUnfinishedEdit()
    {
        org.json.JSONObject was = store.unfinishedEdit();

        if (was == null) return false;

        // A NOTE OF THE RIGHT SHAPE THAT STILL WILL NOT READ is refused like one of the wrong shape (GSP-C4): kept, for the
        // build that wrote it, and reported through `unusableEditNote`.  `unfinishedEdit` checks the note's top level only,
        // so a value of the wrong type inside it throws out of the read; letting that escape would reach
        // `TrainControlUI.getAutonomySession`, which builds no session, and autonomy would stay unopenable at every start.
        // The store's `restoreSetup` puts back what it had before it throws, so the setup is as it was.
        try
        {
            restoreSetup(was);
        }
        catch (RuntimeException noteWillNotRead)
        {
            editNoteWouldNotRead = true;

            return false;
        }

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

        // AND WHAT THE RAILWAY CHANGED ALONE while the edit session was open stays changed (OB-343)
        putBackWhatTheRailwayChangedAlone();

        // AND WHAT THE CARRY REMEMBERED, AND THE RAILWAY'S SQUARES, as before the first move (RSA7-B3, RSA8-B2) - the legs
        // are the snapshot's.
        //
        // **BUT THE NAMES THE EDIT'S LAST BUILD GAVE ARE KEPT, which the memory as it was knows nothing of (OB-343).**  A
        // timetable the railway changed alone during the edit names the Points the railway was built with, and what Cancel
        // puts back names the old ones; the carry traces a name the build lacks through the names its builds have seen, so
        // with the new name forgotten the legs naming it would be dropped by the next load.  The LAST build's names only:
        // each is one square's, and the legs the folds took have been carried through every build before it
        // (`carryTheNamesAcross`).  A name the opening memory has stays as it had it; one it has not is kept until the edit
        // session ends (`keptSquares`), for Cancel's own fold and the load after it, and not into the next session's
        // opening memory.
        if (asTheEditBegan != null)
        {
            Map<String, TileKey> lastSquares = squareOfNameNow;
            Map<String, String> lastBases = baseOfNameNow;

            putBack(asTheEditBegan, false);

            forgetTheNamesKept();

            for (Map.Entry<String, TileKey> named : lastSquares.entrySet())
            {
                if (squareOfNameSeen.containsKey(named.getKey())) continue;

                squareOfNameSeen.put(named.getKey(), named.getValue());
                keptSquares.put(named.getKey(), named.getValue());
            }

            for (Map.Entry<String, String> named : lastBases.entrySet())
            {
                if (baseOfNameSeen.containsKey(named.getKey())) continue;

                baseOfNameSeen.put(named.getKey(), named.getValue());
                keptBases.put(named.getKey(), named.getValue());
            }
        }

        asTheEditBegan = null;

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
        java.util.Map<String, Object> snapshot = store.snapshotPage(page);

        // AND WHAT AN EDIT OF THE PAGE CHANGES OUTSIDE IT (RSA8-B2, RSA8-B3) - see `Remembered`
        snapshot.put(REMEMBERED, remembered());

        return snapshot;
    }

    /**
     * Puts a page's setup back as it was, and rebuilds the graph from it.
     */
    public void restorePage(String page, java.util.Map<String, Object> snapshot)
    {
        if (snapshot == null) return;

        store.restorePage(page, snapshot);

        // AND WHAT THE EDIT CHANGED OUTSIDE THE PAGE (RSA8-B2, RSA8-B3) - see `Remembered`
        Object remembered = snapshot.get(REMEMBERED);

        if (remembered instanceof Remembered) putBack((Remembered) remembered, true);

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
     * The diagram editor calls this when it moves track.  The station designation, facings, arrival restrictions, length
     * and placement are all keyed by square, so they have to move with the caption, or the next reconcile finds a station
     * on a square with no sensor and drops it for good.
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

        // WHETHER ANYTHING IN THE STORE ACTUALLY CHANGED (X8V-B2): `any` above is only "the caller gave me something to look
        // at".  Callers read the answer as "something was forgotten, so write the setup to disk" (`delete` says so), and
        // `deleteSelection` asks once per picked square.
        boolean changed = store.moveTiles(moves, builtOver);

        // AND NO FOLD OF THE RAILWAY BUILT BEFORE THE MOVE (RSA6-B2) - see `squaresMoved`
        boolean moved = false;

        for (Map.Entry<TileKey, TileKey> move : moves == null ? Collections.<TileKey, TileKey>emptyMap().entrySet()
            : moves.entrySet())
        {
            if (move.getKey() != null && move.getValue() != null && !move.getKey().equals(move.getValue()))
            {
                moved = true;
            }
        }

        if (moved) squaresMoved = true;

        // NOR AFTER A SQUARE REMOVED OR BUILT OVER (RSA7-A1) - see `squaresMoved`
        if (builtOver != null && !builtOver.isEmpty()) squaresMoved = true;

        // AND THE RAILWAY'S SQUARES, AND WHAT THE CARRY REMEMBERS, MOVED WITH THE SETUP'S (RSA7-B3, RSA8-B2) - as they were
        // before the first move kept for a Cancel: see `Remembered`
        if (moved)
        {
            if (asTheEditBegan == null) asTheEditBegan = remembered();

            moveTheRailwaysSquares(square -> moves.get(square) != null ? moves.get(square) : square);

            // A NAME MADE FROM A MOVED SQUARE - "page x,y" - is the store's to rename (RSA5-B2), not the memory's to carry
            squareOfNameSeen.entrySet().removeIf(seen -> moves.get(seen.getValue()) != null
                && GraphReducer.generatedName(seen.getValue()).equals(baseOfNameSeen.get(seen.getKey())));

            for (Map.Entry<String, TileKey> seen : squareOfNameSeen.entrySet())
            {
                TileKey to = moves.get(seen.getValue());

                if (to != null) seen.setValue(to);
            }
        }

        // The graph is built from the squares, so it is now describing the old ones, and touched() is what rebuilds it.
        //
        // UNCONDITIONAL, BECAUSE THE REBUILD IS OWED TO THE DIAGRAM (N8-B2): the graph is traced from the SQUARES, so
        // building over a blank square changes it though the store held nothing about that square (a paste, a fill, a
        // palette drop and a clear all reach here with empty squares).  What is expensive is WRITING the setup, and every
        // caller decides that from the return value below; a rebuild is memory.
        // A SQUARE BUILT OVER KEEPS THE DIRECTION OF THE RUN IT JOINS (OB-361): the rebuild gives it the run's, as it gives
        // any square of a run, and says nothing - it is the edit, not a setup that disagreed
        joiningTheirRuns = builtOver == null ? Collections.<TileKey>emptySet() : new LinkedHashSet<>(builtOver);

        try
        {
            touched();
        }
        finally
        {
            joiningTheirRuns = Collections.emptySet();
        }

        // AND WHAT THE REBUILD GAVE THEM, which the caller saves on this answer (RSA75-C1): track laid on a blank square
        // beside a one-way run takes the run's direction, and the store held nothing about that square to forget - so it
        // was in memory only, and the next open logged the run as a correction
        return changed || !graph.getNormalizedRuns().isEmpty() || !graph.getNormalizedToArms().isEmpty();
    }

    /** The squares just built over, which take their runs' direction without its being said (OB-361). */
    private Set<TileKey> joiningTheirRuns = Collections.emptySet();

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
     * Dragging a tile carries whatever was written on it, so rearranging a diagram does not mean placing every caption
     * again by hand.
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

        // Through setCaption, so anything already captioning that station elsewhere on the destination's page -
        // including whatever was on the destination square - goes with it, and its captions on other pages stay.
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

        // A station already shown somewhere on this page is MOVED, not refused: asking to show a name is asking for it to be
        // here.  The old ones are cleared further down, once somewhere new has actually been found, so a station with
        // nowhere to go keeps the caption it had.  Only the ones on this page: its captions on other pages stay (OB-341).

        LayoutDiagram page = pageOf(tile);

        if (page == null || graph == null) return "autosetup.ui.labelNoDiagram";

        LayoutDiagramComponent here = page.getComponent(tile.getX(), tile.getY());

        if (here == null) return "autosetup.ui.labelNoDiagram";

        // Beside the platform where there is room, on it where there is not: a caption written on the sensor sits across the
        // rails.  Below for a station lying north-south, left for one lying east-west, then the other three - a preference,
        // not a rule, because the preferred square is usually occupied.
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

            // Straight THROUGH rather than of type STRAIGHT: a signal or an uncoupler is a plain piece of running line with a
            // fitting on it.
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

            // Inside the part of the page the running diagram DRAWS, which is the box around its components rather than the
            // whole grid: getComponent answers null both for a blank square and for one off the edge, and this loop reads
            // null as "free".  Worked out from the components, not read from the page: in edit mode its bounds are the whole
            // page (AD-C6).
            if (!page.isDrawnWhenRunning(at.getX(), at.getY())) continue;

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
     * Forgets wherever this station was being shown on a page, so that placing it again leaves exactly one there.
     *
     * Only the captions on the page of the square being captioned: a station is shown at most once per page, and may be
     * shown on several (the editor goes per page too), so its captions on other pages stay.
     *
     * Called only once somewhere new has been found: clearing first and then failing to place would
     * answer "show this name" by removing the name that was there.
     *
     * @param station the sensor's square
     * @param except the square the name is now to be shown on, whose page is the one cleared
     */
    private void clearCaptions(TileKey station, TileKey except)
    {
        for (TileKey where : new LinkedHashSet<>(captionsFor(station)))
        {
            if (where.equals(except) || !where.getPage().equals(except.getPage())) continue;

            store.setCaption(where, null);
        }
    }

    /**
     * Whether a station is shown on a square of this page.
     *
     * @param station the sensor's square
     * @param page the page's name
     * @return true when one of its captions sits on that page
     */
    boolean isCaptionedOnPage(TileKey station, String page)
    {
        for (TileKey where : captionsFor(station))
        {
            if (where.getPage().equals(page)) return true;
        }

        return false;
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
     * A label bound a caption to a Point by NAME, which broke on a rename (every page showing the name had to be
     * rewritten), on a station split into several Points (called none of them) and on a name that no longer existed (a
     * caption that looked live and did nothing); a caption keyed to the station's SQUARE has none of these.
     *
     * A label naming a station this setup knows becomes a caption keyed to that station's SQUARE.  One naming nothing is
     * dropped, on the author's instruction: it points at track that does not exist.
     *
     * The setup is written BEFORE the pages are: were a page write to fail the other way round, the labels would be gone
     * from the file and the captions absent from the setup.  This way a failure leaves both, and the migration runs again
     * next time and reaches the same answer.
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
                    // ONE CAPTION PER STATION PER PAGE, AS AT EVERY OTHER DOOR (OB-256, OB-341): `setCaption` and the old-file
                    // import both keep a station to one caption per page, and the store's own door does neither, so a station
                    // the setup already showed on the page would gain a second one.  The setup's caption is the one somebody
                    // chose, so it stays - and the label still leaves the page below, or it would be found again at every
                    // open.  A label on another page than the station's caption is that page's: it becomes one there.
                    if (!isCaptionedOnPage(station, where.getPage())) store.setCaption(where, station);

                    migrated = true;

                    // COUNTED PER PAGE, and added to the total only once that page has been written (VD9-C20): the count and
                    // the page list are printed in one sentence, so a page whose write threw must be in neither.
                    Integer soFar = takenFrom.get(entry.getKey());

                    takenFrom.put(entry.getKey(), soFar == null ? 1 : soFar + 1);
                }
            }
        }

        List<String> failures = new ArrayList<>();

        // Nothing named a station this setup knows, so there is nothing to migrate and nothing to write.  Saving regardless
        // would create a setup file for a layout with no autonomy at all, and rewrite every page that merely CONTAINED a
        // label at every launch, because an unrecognised label is deliberately left where it is.
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
                // Only the ones that became a caption.  A label naming a station this setup has never heard of is left where it
                // is: stripping it would delete the only record that it ever existed, and the instruction to drop orphans was
                // about not DRAWING them, not about editing somebody's diagram.
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

        // Every named square, not only the ones the reduction has Points for: the reduction is built without the excluded
        // pages, so a label naming a station on one of them would match nothing and be stripped anyway.  Excluding a page
        // has to be reversible.
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
        // the list's mode with it (FR-121), or a capture writes the list back read the other way
        "excludedLocsMode",
        // WHERE A STANDING TRAIN'S TAIL LIES, captured with the train (WK7-B1): a side and a road written by a run live only
        // on the running layout.  Replaced like the rest, and removed where the layout no longer carries them, which is a
        // square whose train has left.
        "arrivedFrom", "arrivedAlong");

    /**
     * The per-point keys that say where a train stands: the train, and its tail's side and road (RLV10-B1).  The rest of
     * `POINT_OPERATIONAL_KEYS` is the setup's.
     */
    private static final List<String> PLACEMENT_KEYS = java.util.Arrays.asList("loc", "arrivedFrom", "arrivedAlong");

    /**
     * Squares where trains turn round whose track has no recorded length.
     *
     * Adam: "Add notices to the autonomy editor to add track lengths between stations and switches that
     * accept reversal (if any other track length is set anywhere)."
     *
     * A train reversing into a berth shorter than itself ends up standing across the switch behind it.
     * `Layout.isPathClear` refuses that, but it can only refuse what it can measure, and unmeasured track is unknown
     * rather than short.
     *
     * **What this set is** (`SVN-B1`, `VD9-B6`):
     *
     * - `Layout.measuredRoomAtTheEndOf` walks the path's **edges** backwards and **stops at the first one that crosses a
     *   switch**; it never looks past the last switch, so an unmeasured stretch beyond it blinds nothing.
     * - `GraphReducer.roomAfterTheLastSwitch` is that number: one edge's **tiles**, walked backwards from the end,
     *   stopping at the first switch, summing what is measured and treating an unmeasured tile as nothing.  `-1` only
     *   when NOT ONE of them has a length (MON-C6).
     * - `unmeasuredAfterTheLastSwitch` is the same walk returning the tiles, which is what this asks for.  **So for the
     *   last edge the notice and the guard agree by construction.**
     *
     * **The square's own length is the first thing that stretch contains.**  An unmeasured berth square contributes
     * nothing to the total, so the guard under-counts by exactly that square, which is worth a notice.
     *
     * **Two ways they still disagree.**  Under-reports: when the arriving edge crosses no switch the guard carries on to the
     * edge before it, and this only looks at edges arriving at the reversal square, so a notice can be cleared with the
     * guard still blind.  Over-reports: on an edge that crosses no switch the guard asks only that the edge's own length be
     * positive, while this names every unmeasured tile on it.  Neither is worth chasing without a ruling: closing the first
     * changes what the count means, and Adam has accepted the count as it stands ("20 warnings sounds OK").
     *
     * **The condition is his, and it is what stops this being a nag.** A railway that records no lengths anywhere has
     * decided not to model them; one that records some has started, and these are the ones that matter most.
     *
     * **ONE ENTRY PER REVERSAL SQUARE** (OB-171).  Adam: *"warnings ... fire on many tiles along a line.  Dedupe them,
     * one per segment between a switch and a station."*  The notice does not go until the whole stretch is measured,
     * because that is what the guard needs; what is returned is the square trains turn at, mapped to how many squares
     * are still missing a length.
     *
     * @return the squares to ask about, each mapped to the number of unmeasured squares its guard
     *         needs, empty when the layout measures nothing
     */
    public java.util.Map<TileKey, Integer> reversalsWithoutLength()
    {
        java.util.Map<TileKey, Integer> out = new java.util.LinkedHashMap<>();

        if (store == null || reducer == null) return out;

        // Asked of what has been recorded, not of the graph's points: a length belongs to a SQUARE, and the plain track
        // between two sensors - the thing somebody actually measures - carries no point at all.
        if (!store.measuresAnyTrack()) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            // Anywhere a train may turn round - compulsory or optional, the room behind it is the
            // same question.
            if (!isTurnAround(tile)) continue;

            java.util.Set<TileKey> missing = new java.util.LinkedHashSet<>();

            // THE REVERSAL SQUARE IS PART OF THE STRETCH, not a separate thing to ask for (MT-364): measuring the track behind it
            // must silence the notice.  The walk already includes the end tile, as `roomAfterTheLastSwitch` does, so the square
            // is asked for only where no edge arrives, which the walk cannot speak for: a square with no approach has no
            // stretch to measure.
            boolean anApproachExists = false;

            // AND THE TRACK BEHIND IT, BACK TO THE SWITCH (Adam's ruling 2): the guard needs the stretch that
            // bounds the train, and a stretch with nothing measured answers "unknown".
            //
            // **WHY IT STILL REPORTS A PARTLY MEASURED STRETCH** (MON-C6): an unmeasured tile contributes nothing and the
            // rest still counts, so a half-measured stretch makes the guard PESSIMISTIC - a train that would fit is refused
            // rather than admitted.  That is the safe direction and still worth telling somebody; whether it earns its
            // place in a list Adam has called a wall is his call (MT-305).
            //
            // WHICH squares comes from ruling 1: the stretch after the LAST switch on the edge arriving here, because that
            // is the one the room is measured over.
            for (GraphReducer.ReducedEdge arriving : reducer.getEdges())
            {
                if (!arriving.getEnd().equals(tile)) continue;

                // A SIDE NO TRAIN STOPS FROM IS STILL ASKED ABOUT (Adam, TDA-C8: *"Arrivals THAT STOP THERE
                // should only be allowed from the configured side(s).  Turning shouldn't need to factor this in, since
                // the former would govern the behavior."*).  A barred side keeps its turning copy, so a train may come in
                // that way and turn, and the room rule judges that turn over this stretch.  The two berth checks skip a
                // barred side, because nothing stops there; this one does not.
                anApproachExists = true;

                // AN ANSWERED 0 IS NOT MISSING (Adam: "stop listing answered zeros as missing").  It is
                // measured track of no length, and the operator has answered it, so it is not asked for
                // again.
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
     * **Every leg, cut at switches** - Adam's ruling on review MAL-B1.  The room rule stops at the last switch,
     * but the FR-087 allowance adds whole legs back until a reversal, and the tail and berth walks spend the switch square
     * and the track before it, so every leg is read.  Cutting at switches keeps every rule exact: an even share of a piece
     * never lands on the far side of a switch, where the room rule would not count it.
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
     * spend a train's length over every square of a leg, the switch included, so every leg is read and the ruling is to cut
     * them at switches.
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

        // A SQUARE TWO LEGS RUN OVER IS CUT OUT, as a switch is (Adam, SET-B2).
        java.util.Set<TileKey> shared = sharedSquaresALengthRuleReads();

        for (java.util.List<TileKey> leg : legsOnce())
        {
            java.util.List<TileKey> piece = new java.util.ArrayList<>();
            int firstIndex = -1;

            for (int i = 0; i <= leg.size(); i++)
            {
                // A ROUTE TILE IS IN NO PIECE AND DOES NOT CUT ONE (Adam, OB-273: *"a route tile should
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
     * Every switch square on a leg autonomy uses - a switch or a permanent turnout, as `isSwitchSquare` says
     * (VD18-B3).  A switch is in no piece - its share would sit where the room rule
     * does not count it - so switches are asked for on their own, one turnout length for a page (Adam:
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
     * The WHOLE piece, not each square - Adam: *"It is only indeterminate if the entire logical segment has
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
     * The least whole length a piece can be given: 0 (OB-274), where a deliberate 0 is an answer - measured track of no
     * length.
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
     * Shares a piece's whole length out over its squares (per stretch).
     *
     * Only a piece that adds up to nothing is written - one that has a length is measured, by the ruling above.  The
     * whole length is shared evenly; a square may get 0 where there are fewer units than squares.
     *
     * **ANY UNIT LEFT OVER GOES FIRST TO A SQUARE A TRAIN STANDS ON** (MAL-B2), which is where Adam puts the larger share
     * when he measures by hand - TunnelLongPark is 2 on its sensor square and 1 behind.  Every walk spends that square
     * first (OB-278), so where the unit goes decides only how many squares of THIS piece a short train's tail claims, and
     * no other road joins a piece between its switches.  Past the standing squares the rest go in the order the leg runs;
     * the room rule and the FR-087 allowance read only the total, so for them the split changes nothing.
     *
     * @param stretch the piece
     * @param wholeLength its whole length, at least 1
     * @return whether it was written
     */
    public boolean assignStretchLength(Stretch stretch, int wholeLength)
    {
        if (stretch == null || wholeLength < 0 || measuredIn(stretch) > 0) return false;

        // A DELIBERATE 0 IS AN ANSWER (OB-274): every square of the piece is recorded as answered, so the walk does
        // not offer it again, and every length rule but the own-tail one (OB-300) reads it as measured track of no length
        // (TDU-C6).
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
     * Gives one turnout length to every switch here that has none (Adam: "One length for all switches").
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

            // 0 ANSWERS IT (OB-274) - two switches back to back are his adjacent tracks - and every length rule but the
            // own-tail one (OB-300) reads it as measured track of no length (TDU-C6).
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
     * The stations on this page that will still take a train of any length, in the order Mass Assign Station Max Train
     * Lengths asks about them: row by row, left to right.
     *
     * Adam: *"add a similar feature to walk stations that don't have a max length set up, so I can enter
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
     * Adam: *"For crossings: if its length is set, count that length once in each direction."*  That is what the
     * reduction already does (`GraphReducer.sumLength` adds every tile of every leg, so a crossing of 3 adds 3 to each road
     * over it), and it is only right if no leg's PIECE contains it, because a piece's whole length is shared over its
     * squares and would then be counted on the other road too.  So the square is cut out of every piece and asked for on
     * its own, as a switch is (MAL-B1).
     *
     * **Two ROADS THAT SHARE METAL, not two legs.**  A crossing's roads cross, so one length counts on both and the square
     * is asked for once.  A DOUBLE CURVE's curves never touch (AUR-B1); it is here only because one number the operator
     * types measures both (OP2-C4).  What makes a square shared is its geometry (`TilePorts` gives it two separate roads)
     * AND legs actually running over it on more than one of them; a double curve with track on only one road stays
     * ordinary track.
     *
     * **Intermediate occurrences only.**  A sensor square ends several legs (MAL-C2's first-piece-keeps-it rule) but is an
     * endpoint, never track a leg runs over.  **Switches are not listed**: they have their own step and are already cut out.
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
            // EVERY CROSSING COUNTS, INCLUDING TWO BY THE SAME LEG (SVA-C2): a figure of eight runs one leg over its own
            // crossing twice, so count occurrences rather than legs; the ends are where the leg STOPS, not track it runs over.
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

    /**
     * Whether a length rule's piece is cut at this square, and the square asked for with the switches: what
     * `GraphReducer.boundsTheRoom` asks of the room walk - a switch or a permanent turnout, and not a square of a
     * two-square crossing (VD18-B3).
     *
     * **A PERMANENT TURNOUT CUTS A PIECE AS A SWITCH DOES.**  The room walk stops at one (OB-233: a permanent turnout is
     * still the last switch), so a piece that ran over it would share the operator's
     * typed length onto the turnout and the track beyond it.  `isSwitch()` alone names only the six throwable types, so
     * the permanent ones are asked separately.  A two-square crossing's squares are permanent turnouts drawn as one
     * crossing, which ends no room (OB-320), so they stay track here.
     *
     * @param tile a square
     * @return true where a piece ends and the square is asked for with the switches
     */
    private boolean isSwitchSquare(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagramComponent component = getGraph().getTiles().get(tile);

        if (component == null || getGraph().crossingPartner(tile) != null) return false;

        return component.isSwitch() || TileGraph.isPermanentTurnout(component.getType());
    }

    /**
     * Whether a berth's room ends at this square: a switch or a permanent turnout, where the room walk ends
     * (`GraphReducer.boundsTheRoom`, OB-233); or a crossing another road runs over, where the berth rule refuses
     * (TDA-C7, TDA5-C1).
     *
     * **Known limit** (TDA3-C2, narrowed by TDA5-C1): a crossing whose other road runs between two sensors no train can
     * reach is still taken for a stop.  The reduction has that road, so this finds it; the build emits no rail over it,
     * so the rule has nothing there to refuse on, and the notices warn of a refusal that does not come.  The warning
     * side, and a crossing nobody builds.
     *
     * @param tile a square
     * @param berth the berth the room is counted for
     * @return true where the berth's measured room ends
     */
    private boolean endsTheBerthsRoom(TileKey tile, TileKey berth)
    {
        org.traincontrol.base.LayoutDiagramComponent component = getGraph().getTiles().get(tile);

        if (component == null) return false;

        // A TWO-SQUARE CROSSING IS A CROSSING (OB-320), not the two turnouts it is drawn with
        if (getGraph().crossingPartner(tile) != null) return anotherRoadRunsOver(tile, berth);

        if (component.isSwitch() || TileGraph.isPermanentTurnout(component.getType())) return true;

        return component.getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.CROSSING
            && anotherRoadRunsOver(tile, berth);
    }

    /**
     * Whether a road that neither starts nor ends at this berth runs over this square - the berth rule's own question
     * (`Layout.whyABerthCannotHoldIt` refuses on a claimed place shared with a road, and passes over a road that starts
     * or ends at the berth, as a switch's other leg into the same berth does) (TDA5-C1).
     *
     * @param tile a square
     * @param berth the berth
     * @return true where such a road runs over it
     */
    private boolean anotherRoadRunsOver(TileKey tile, TileKey berth)
    {
        if (reducer == null) return false;

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            if (berth.equals(edge.getStart()) || berth.equals(edge.getEnd())) continue;

            for (GraphReducer.TileStep step : edge.getPath())
            {
                if (tile.equals(step.getTile())) return true;
            }
        }

        return false;
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
     * Squares that emit an arrival copy a train can be sent to and then never leave.
     *
     * Adam: "We need a warning for instances like the previous version of this."  A copy of a square can have two ways in and
     * none out - a destination autonomy may choose, from which no train can depart.
     *
     * **The existing checks cannot see this.**  They ask about the SQUARE (`STATION_REACHES_NOTHING`, `ARRIVAL_TRAPPED`
     * and the rest work in TileKeys), and a square is emitted as one Point per side a train can arrive by, so a reachable
     * square passes while one of its copies has no way in or out.  A rule about copies cannot be enforced by looking at
     * squares, so this reads the graph as BUILT, where the copies exist, and reports the square that produced a bad one.
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
     * Worth its own warning because the consequence is less obvious.  A train cannot be SENT there, but one that starts
     * there, or is left there by hand, is one Return Home will not move - the staging planner never moves a locomotive
     * off a square with no way back in, because it could never undo the move.  If such a copy sits on a through route, a
     * train parked on it blocks everything behind it and Return Home can only answer that it found no arrangement.
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

        // THE BUILDER'S OWN MAPPING, not one taken apart by hand: splitting the copy's name works only for a square somebody
        // has named, and on an unnamed square the check found nothing.  `tilesByName` is what the builder itself uses, so the
        // two cannot disagree about what a Point is called.
        java.util.Map<String, TileKey> byName = named != null ? named : builder(null).tilesByName();

        java.util.Set<String> good = wantNoWayOut ? hasOut : hasIn;

        for (String copy : stations)
        {
            if (good.contains(copy)) continue;

            TileKey tile = byName.get(copy);

            if (tile == null || out.containsKey(tile)) continue;

            // ONLY WHERE THE SQUARE ITSELF IS HEALTHY.  Where EVERY copy of a square is stuck the square is stuck, and the
            // square-level checks (`checkStationUnreachable`, `checkStationReachesNothing`) already say so; saying it again
            // as an ERROR, which refuses to start autonomy, is worse than noise.  What no square-level check can see is a
            // HEALTHY square with one trapped arrival.
            if (!hasAHealthySibling(copy, tile, good, stations, byName)) continue;

            out.put(tile, copy);
        }

        return out;
    }

    /**
     * Squares the build emits with an arrival that can go nowhere (DD-A7).
     *
     * A copy IS an arrival - the builder emits one Point per side a train can come in by - so a copy with no outgoing
     * edge is a train that arrived and cannot leave.
     *
     * STATIONS ONLY, and only an arrival a train can make (OB-361; Adam: *"MainLine isn't a station, and it is impossible
     * for trains to arrive from the west anyway, so both these conditions should disqualify the notice."*): no train is
     * sent to a plain point, and a copy no train reaches is a move no train makes.
     *
     * Each with the sides its trapped arrivals come in by (OB-201) - the builder's own copy says.
     *
     * Empty when the setup will not build, deliberately - see `builtForInspection`.
     *
     * @param built the inspected configuration, or null
     * @param named the builder's own name-to-tile mapping
     * @param arrivals the builder's own name-to-arrival-side mapping
     * @return the squares, each to the sides its trapped arrivals come in by
     */
    private Map<TileKey, Set<Side>> tilesWithATrappedArrival(org.json.JSONObject built,
        java.util.Map<String, TileKey> named, java.util.Map<String, Side> arrivals)
    {
        Map<TileKey, Set<Side>> out = new LinkedHashMap<>();

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

        java.util.Set<String> reached = copiesATrainReaches(built);

        for (int i = 0; i < points.length(); i++)
        {
            String name = points.getJSONObject(i).getString("name");

            if (hasOut.contains(name)) continue;

            TileKey tile = byName.get(name);

            if (tile == null) continue;

            // NOT A PLAIN POINT, NOR AN ARRIVAL NO TRAIN MAKES (OB-361)
            if (!store.isStation(tile) || !reached.contains(name)) continue;

            // A square the operator has said trains may turn round at is not trapped: turning IS the
            // way out, and the builder expresses that by emitting the turning copy.
            if (isTurnAround(tile)) continue;

            // AND A SQUARE THAT WAS NEVER SPLIT HAS NO ARRIVAL TO TRAP: a tile nothing arrives at by any side of the grid is
            // emitted whole, and a whole tile with no way out is a dead end in the track, not a trapped arrival (a link
            // lands a train by a real side, AMG-C2).  Reporting such squares would tell the operator to fix something that
            // is not there.
            if (arrivalSides(tile).isEmpty()) continue;

            Side side = arrivals == null ? null : arrivals.get(name);

            // A SIDE TRAINS MAY NOT ARRIVE BY IS NO TRAP (RSA37-B1).  Its copy is no station, so no train is sent to stop
            // there, and with no way out none passes through it.  Closing arrivals from that side is one of the three ways
            // out the finding offers, so counting its copy would leave the same warning after the advice was taken.
            if (side != null && getBarredArrivals(tile).contains(side)) continue;

            Set<Side> sides = out.computeIfAbsent(tile, k -> new LinkedHashSet<Side>());

            if (side != null) sides.add(side);
        }

        // A copy the builder names no side for - none should, since only a split square is reported - is named by the
        // square's ways in, rather than by nothing
        for (Map.Entry<TileKey, Set<Side>> square : out.entrySet())
        {
            if (square.getValue().isEmpty()) square.getValue().addAll(arrivalSides(square.getKey()));
        }

        return out;
    }

    /**
     * The copies a train can arrive at on the built railway: walked on along its edges from every station and every copy a
     * train stands on, and the copies trains stand on (OB-361).
     *
     * @param built the built configuration
     * @return the names of those copies
     */
    private static java.util.Set<String> copiesATrainReaches(org.json.JSONObject built)
    {
        Map<String, List<String>> onward = new java.util.HashMap<>();

        org.json.JSONArray edges = built.getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONObject edge = edges.getJSONObject(i);

            onward.computeIfAbsent(edge.getString("start"), k -> new ArrayList<String>()).add(edge.getString("end"));
        }

        java.util.Set<String> reached = new java.util.HashSet<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Deque<String> toVisit = new java.util.ArrayDeque<>();

        org.json.JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.getJSONObject(i);

            boolean standing = point.has(AutonomyBuilder.LOCOMOTIVE);

            if (standing) reached.add(point.getString("name"));

            if ((standing || point.optBoolean("station", false)) && seen.add(point.getString("name")))
            {
                toVisit.add(point.getString("name"));
            }
        }

        while (!toVisit.isEmpty())
        {
            List<String> next = onward.get(toVisit.poll());

            if (next == null) continue;

            for (String copy : next)
            {
                reached.add(copy);

                if (seen.add(copy)) toVisit.add(copy);
            }
        }

        return reached;
    }

    /**
     * The trapped squares whose trapped sides the arrivals menu can close (RSA37-B1).
     *
     * @param trapped the squares, each to the sides its trapped arrivals come in by
     * @return those `arrivalsCanBeClosed` says yes to
     */
    private Set<TileKey> arrivalsClosableAt(Map<TileKey, Set<Side>> trapped)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (Map.Entry<TileKey, Set<Side>> square : trapped.entrySet())
        {
            if (arrivalsCanBeClosed(square.getKey(), square.getValue())) out.add(square.getKey());
        }

        return out;
    }

    /**
     * Whether the arrivals menu can close these sides of a square, as well as any already closed: the square is a
     * station with more than one way in, and one stays open (RSA37-B1).
     *
     * The menu's own rule, asked by the menu and by the trapped-arrival finding alike, so the advice the finding gives is
     * advice the menu takes.  It offered "close arrivals from the east and west" at a station with no other way in, whose
     * last open side the menu will not shut, and at a plain point, which has no arrivals menu.
     *
     * @param tile the square
     * @param sides the sides to close
     * @return whether they can be
     */
    public boolean arrivalsCanBeClosed(TileKey tile, java.util.Collection<Side> sides)
    {
        if (!store.isStation(tile)) return false;

        List<Side> ways = arrivalSides(tile);

        Set<Side> closed = new LinkedHashSet<>(getBarredArrivals(tile));

        closed.addAll(sides);
        closed.retainAll(ways);

        return ways.size() > 1 && closed.size() < ways.size();
    }

    /**
     * The termini a train that cannot reverse can be backed into: reached tail first, after an odd number of turns on the
     * way.  A train rested on a terminus sets off turned - Return Home's two-step parking (AMH-B1: "That's why we started
     * the 2 step process for parking, which is OK in my opinion.") - and each reversing point it passes turns it again,
     * as MT-245's S - reversing M - terminus T, which Return Home drives (HomeStaging's turn-on-the-way).  A reversing
     * point on a stub is no way in: a train turned there and sent straight back where it came from is a route no door
     * drives (RSA66-C3).  So: counted from every square, the turns on the way, odd at a terminus not the one set off
     * from; never through a terminus, never into a square out of service, never straight back from a reversing point to
     * the square before it.
     *
     * @param built the built configuration, or null
     * @param tiles each point's square, by name
     * @return the squares, or null where there is no build to walk
     */
    public static java.util.Set<TileKey> berthsATrainCanBackInto(org.json.JSONObject built,
        java.util.Map<String, TileKey> tiles)
    {
        if (built == null || tiles == null) return null;

        org.json.JSONArray points = built.optJSONArray("points");
        org.json.JSONArray edges = built.optJSONArray("edges");

        if (points == null || edges == null) return null;

        java.util.Map<String, org.json.JSONObject> byName = new java.util.LinkedHashMap<>();

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.optJSONObject(i);

            if (point != null && point.has("name")) byName.put(point.getString("name"), point);
        }

        java.util.Map<String, java.util.List<String>> onwards = new java.util.HashMap<>();

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONObject edge = edges.optJSONObject(i);

            if (edge == null) continue;

            onwards.computeIfAbsent(edge.optString("start"), k -> new java.util.ArrayList<>()).add(edge.optString("end"));
        }

        java.util.Set<TileKey> out = new java.util.LinkedHashSet<>();

        for (org.json.JSONObject start : byName.values())
        {
            if (!start.optBoolean("active", true)) continue;

            String from = start.getString("name");

            TileKey setOffFrom = tiles.get(from);

            // a train rested on a terminus sets off turned
            boolean turned = start.optBoolean("terminus", false);

            // each state: where, turned an odd number of times, and the square before it
            java.util.Set<String> seen = new java.util.HashSet<>();
            java.util.Deque<Object[]> queue = new java.util.ArrayDeque<>();

            queue.add(new Object[] {from, turned, null});
            seen.add(from + "|" + turned + "|null");

            while (!queue.isEmpty())
            {
                Object[] at = queue.poll();

                String here = (String) at[0];
                boolean odd = (Boolean) at[1];
                TileKey before = (TileKey) at[2];

                boolean turnsHere = byName.get(here).optBoolean("reversing", false);

                for (String name : onwards.getOrDefault(here, java.util.Collections.emptyList()))
                {
                    org.json.JSONObject next = byName.get(name);

                    if (next == null || !next.optBoolean("active", true)) continue;

                    TileKey square = tiles.get(name);

                    // not straight back from a reversing point to the square before it: a stub
                    if (turnsHere && before != null && before.equals(square)) continue;

                    if (next.optBoolean("terminus", false))
                    {
                        if (odd && square != null && !square.equals(setOffFrom)) out.add(square);

                        // a terminus is arrived at, never driven through
                        continue;
                    }

                    boolean onwardOdd = odd ^ next.optBoolean("reversing", false);

                    String key = name + "|" + onwardOdd + "|" + tiles.get(here);

                    if (seen.add(key)) queue.add(new Object[] {name, onwardOdd, tiles.get(here)});
                }
            }
        }

        return out;
    }

    /**
     * The built graph as JSON, or null when the setup will not build.
     *
     * FOR INSPECTION, which is what the copy checks are.  buildConfiguration() answers for a configuration that has been
     * initialised and saved; asked on a session that has not, it returns the stations and no edges, and on a test machine
     * it reached the DEFAULT layout to find them, which on Adam's is his real railway.
     *
     * A setup that will not build has louder problems than a trapped arrival, and every one of them is already reported.
     * Saying nothing is better than saying something wrong.
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
     * Adam: **"yes, extend the check to catch that shape too."**  A station a train can arrive at and depart
     * from and still never get anywhere: one way in, one way out, and the way out leads only to track that comes back.
     * The two checks above pass it, because it has an edge in each direction; it is a dead end with a siding on it.
     *
     * **Another station, not another Point.**  Track a train may pass but not stop at is not somewhere it can be sent, so
     * a station whose only way on is plain track is as stuck as one with no way on at all.
     *
     * **And a different SQUARE.**  Where track loops back, an arrival can reach its own turning twin, and the twin is the
     * same platform: counting that as having got somewhere would report nothing on the very shape this exists for.
     *
     * Copies with no way out at all are left to the check above, which reports them: two messages on one square saying
     * the same thing in different words would be noise.
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
     * Whatever a builder is asked for - the file, the naming, the coordinates - must describe the same railway, so there is
     * one place that says what that railway is.
     *
     * @param globals the run-wide settings, or null when only the naming is wanted
     * @return a fresh builder
     */
    public AutonomyBuilder builder(AutonomyBuilder.Globals globals)
    {
        return builder(reducer, globals);
    }

    /**
     * The same over a reduction of its own - one tried, not installed (RSA5-A2).
     *
     * @param reduction the reduction
     * @param globals the run-wide settings, or null when only the naming is wanted
     * @return a fresh builder
     */
    private AutonomyBuilder builder(GraphReducer reduction, AutonomyBuilder.Globals globals)
    {
        return new AutonomyBuilder(reduction, globals)
            .withPointExtras(pointExtras())
            .withReversibleTiles(reversibleTiles())
            .withMandatoryTurns(mandatoryTurnTiles())
            .withParkingTiles(parkingTiles())
            .withBarredArrivals(barredArrivals())
            .withProtectingSignals(protectingSignalNames())
            .withEntrySignals(entrySignalNames())
            .withBlockingPoints(store.getBlockingPoints())
            .withNamesOutOfPlay(namesOutOfPlay())
            .withPiecesToMeasure(piecesToMeasure());
    }

    /**
     * What Mass Assign Lengths would ask a length for, square by square, keyed as it asks (OB-297, ADA-C1, ADA2-C7): each
     * piece still wanting one is one answer; all of a page's switches with no length are one more, and all its crossings
     * one more.  In the order its walk asks them, so a switch two roads share is answered as a switch.
     *
     * @return the squares, each to the key of the answer it is asked for in
     */
    private java.util.Map<TileKey, String> piecesToMeasure()
    {
        java.util.Map<TileKey, String> out = new LinkedHashMap<>();

        int piece = 0;

        for (Stretch stretch : stretchesNeedingALength())
        {
            piece++;

            for (TileKey tile : stretch.getTiles()) out.put(tile, "piece " + piece);
        }

        for (TileKey tile : switchesALengthRuleReads())
        {
            if (!out.containsKey(tile) && squareNeedsALength(tile)) out.put(tile, "switches on " + tile.getPage());
        }

        for (TileKey tile : sharedSquaresALengthRuleReads())
        {
            if (!out.containsKey(tile) && squareNeedsALength(tile)) out.put(tile, "crossings on " + tile.getPage());
        }

        return out;
    }

    /**
     * @param station a station's square
     * @return the square of the first signal protecting it, or null
     */
    public TileKey getProtectingSignal(TileKey station)
    {
        // A STATION'S (GSE-C4): a square that is not one now keeps its guards for when it is, and they guard nothing
        return store.isStation(station) ? store.getProtectingSignal(station) : null;
    }

    /**
     * @param station a station's square
     * @return the squares of every signal protecting it, in the order they were paired
     */
    public List<TileKey> getProtectingSignals(TileKey station)
    {
        // A STATION'S (GSE-C4), as `getProtectingSignal`
        return store.isStation(station) ? store.getProtectingSignals(station) : new ArrayList<TileKey>();
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
        // A STATION'S (GSE-C4), as `getProtectingSignal`
        return store.isStation(station) ? store.getEntrySignals(station) : new ArrayList<TileKey>();
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
        return signalNames(stationsOnly(store.getProtectingSignals()));
    }

    /**
     * The entry-guard pairings as accessory names, for the build (FR-096) - the same join, asked of the other list.
     *
     * @return station square to accessory names
     */
    public Map<TileKey, List<String>> entrySignalNames()
    {
        return signalNames(stationsOnly(store.getEntrySignals()));
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

    /**
     * Only the entries about squares that are stations now (GSE-C4).
     *
     * A station that is demoted KEEPS its station settings - its caption, the sides it bars arrivals by, its exit and
     * entry guards - and has them again when it is promoted, ignored meanwhile as its maximum train length (OB-291)
     * and its blockers (AMS-B2) already were.  The store keeps them; the build, the checks and the editor read them
     * through this, so a square that is not a station has none of them.
     *
     * @param bySquare a store map keyed by station square
     * @param <V> what each square holds
     * @return a copy holding the stations' entries only
     */
    private <V> Map<TileKey, V> stationsOnly(Map<TileKey, V> bySquare)
    {
        Map<TileKey, V> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, V> entry : bySquare.entrySet())
        {
            if (entry.getKey() != null && store.isStation(entry.getKey())) out.put(entry.getKey(), entry.getValue());
        }

        return out;
    }

    /**
     * Which sides each station refuses to let trains arrive by.
     *
     * @return square to barred sides, only for the squares that restrict anything
     */
    public Map<TileKey, Set<Side>> barredArrivals()
    {
        return stationsOnly(store.getBarredArrivals());
    }

    /**
     * @param tile a station's square
     * @return the sides trains may not arrive by, empty when it takes them from anywhere
     */
    public Set<Side> getBarredArrivals(TileKey tile)
    {
        // A STATION'S (GSE-C4): a square that is not one now keeps the sides it barred, for when it is, and bars nothing
        Set<Side> barred = store.isStation(tile) ? store.getBarredArrivals(tile) : new LinkedHashSet<Side>();

        if (barred.isEmpty()) return barred;

        // Only the sides that still exist: a restriction is stored against a side of the square as the diagram was when it
        // was set, and the diagram moves.  The stale side is dead in the build (no copy for it) but was still counted, and
        // the count decides whether the menu will let you shut another side, so a station could end up with every box
        // ticked, every box disabled, and nothing on screen to say why.
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
     * `arrivalSides` is the geometry: every side a reduced edge reaches this square by.  It is right for the editor's own
     * "Trains May Arrive" menu, which has to offer a barred side so it can be un-barred, and wrong for anything asking
     * where a train came FROM, because a side the operator has closed is a side nothing arrives by.  Asked of the
     * geometry, a station barring one of its two ways in (BottomMainA bars E) would defeat `ArrivalSidePrompt.suggestedFor`'s
     * first and best rule - *"one way in that is not the way it is pointing"*.
     *
     * Barred sides are not SUBTRACTED blindly: `getBarredArrivals` already narrows a stored set to the sides the square
     * really has.
     *
     * @param tile the square
     * @return the sides, in the build's own order, with the barred ones left out
     */
    public List<Side> unbarredArrivalSides(TileKey tile)
    {
        List<Side> sides = new java.util.ArrayList<>(arrivalSides(tile));

        sides.removeAll(getBarredArrivals(tile));

        // EVERY SIDE BARRED IS NOT AN ANSWER: the editor will not let the last way in be closed, but a diagram edit can
        // arrive at it from the other side, and a placement door handed an empty list falls back to the geometry.
        return sides.isEmpty() ? arrivalSides(tile) : sides;
    }

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
     * Derived once and held until the setup changes, because it is asked for constantly - once per Point on every feedback
     * event, in places - and because deriving it twice is how the answers came to disagree.
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
     * The labels are updated once per Point on every feedback event, from the control station's own thread, while the
     * event thread is free to edit the configuration (setPointProperty is documented as usable while autonomy runs).
     * Deriving lazily would have the feedback thread walk the configuration's JSONObjects, backed by plain HashMaps, while
     * the event thread writes to them; a volatile field publishes the REFERENCE safely and says nothing about the
     * derivation.  Called from the two places that invalidate it, both on the event thread, so a reader only ever sees a
     * finished immutable index.
     *
     * @return the new index, also stored
     */
    private StationIndex deriveStationIndex()
    {
        AutonomyBuilder naming = reducer == null ? null : builder(null);

        StationIndex derived = naming == null ? StationIndex.EMPTY : new StationIndex(naming);

        stationIndex = derived;

        // AND THE NAMES THE CONFIGURATIONS STORE CARRIED TO THE NAMES THIS BUILD GIVES (RSA3-B1, RSA4-B1, RSA4-C4).  Here
        // rather than in `rebuild`, because every edit that can rename a Point comes through here and not all of them
        // rebuild: a square marked as somewhere trains may turn round gains its turning copies, named, with no rebuild.
        if (naming != null) carryTheNamesAcross(naming);

        return derived;
    }

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
     * A square is several Points - one per side a train can arrive by - so a train standing at BottomMainB would be offered
     * a path to BottomMainB, the copy facing the other way; going there is not a journey.  Asked HERE rather than of the
     * running layout, which cannot answer it: its obvious key is the sensor, and a station and its approach guard
     * legitimately share one and are genuinely two places.  What makes copies copies is the SQUARE they came from, and
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

    /**
     * Every Point of the running configuration that stands for one square of the diagram.
     *
     * @param baseName the caption on the diagram
     * @return the emitted names, in the order they were emitted
     */
    public List<String> pointNamesFor(String baseName)
    {
        return getStationIndex().pointNamesFor(baseName);
    }

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

    /**
     * What the generated configuration calls the Point on a square.
     *
     * The BASE name - the one a diagram caption carries and the one anything looking a Point up by caption will find.
     * Not the authored name, which can be blank or a duplicate; uniqueNames is what settles both.
     *
     * @param tile
     * @return the name, or null when the square is not a Point
     */
    public String pointNameForTile(TileKey tile)
    {
        return getStationIndex().nameOf(tile);
    }

    /**
     * What to call a square in front of the user: its authored name, then its sensor, then where it is.
     *
     * This lives here, not in AutonomyEditorPanel, because OB-112's diagram right-click menu, in the other window, needs it
     * too, and copying the three lines into the second menu is how the two would come to disagree about a square.  A
     * square somebody named is called that, a sensor square is known by the address printed on the diagram, and everything
     * else has only its position.
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

    /**
     * The squares where a train may turn round, which the builder emits as several Points each.
     *
     * Two ways to be one: a station marked TERMINUS, where a train ends its run and reverses (as a single Point it would
     * be a dead end for routing, since isPathClear refuses any path with a terminus in the middle), and anything else
     * marked CAN REVERSE, a place a train changes direction on its way somewhere else.
     *
     * @return the marked tiles
     */
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
     * Whether every train arriving here must turn round, rather than merely being able to.
     * @param tile
     * @return
     */
    public boolean isMustTurnAround(TileKey tile)
    {
        return Boolean.TRUE.equals(getPointProperty(tile, AutonomyBuilder.MUST_REVERSE));
    }

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
     * `Layout.isSendableDestination` - `isDestination() && isActive() && isAutoDestination() && !isReversing()` - and
     * two of those four are asked here:
     *
     *   - `isAutoDestination`, the one the menu calls **Can Be Chosen in Full Autonomy**;
     *   - `isActive` (E8-B1), which the menu writes when a square is switched out of service.
     *
     * `!isReversing()` cannot be asked of a square: it is a property of a COPY, and a may-reverse square keeps a plain
     * copy autonomy can choose, so a square-level answer would refuse squares the runtime accepts.  `isDestination` is
     * `store.isStation`.  `isMustTurnAround` is NOT a clause: a compulsory-turn square that stops trains is emitted with
     * `terminus:true`, whose `isReversing()` is false, so the runtime chooses it.
     *
     * Adam: **"narrow the notice to match the runtime - autonomy should only allow a turn at a point if the 'allow in
     * autonomy' option is checked, otherwise the train may only pass through in its current direction."**  So the two
     * markings are independent - *Changing Direction* says what happens when a train ARRIVES, this switch says who may
     * send one - and `docs/reference/behaviour.md` section 3 carries the ruling.
     * `core.testACompulsoryTurnIsChosenLikeAnyOtherStation` asserts on `single-switch`, where the spellings can differ.
     *
     * Three readers depend on the width: the magenta leg colour, the Auto tier's "reachable and never chosen" notice, and
     * `AutonomyChecks.checkReversingGoesSomewhere` (Adam: *"Make it a notice."*), which must not count a
     * parking berth as somewhere to go.
     *
     * @return the tiles that are stations autonomy is told to leave alone
     */
    public Set<TileKey> stationsAutonomyWillNotChoose()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            // ONE CLAUSE, AND IT IS THE RUNTIME'S (OB-195).  `isMustTurnAround` is not part of who may send a train: a
            // compulsory turn that stops is built as a TERMINUS, whose `isReversing()` is false, so asking it would refuse
            // a square the runtime accepts.  Turning round is governed by `isAutoDestination` (see the javadoc).
            //
            // SHUT COUNTS TOO (E8-B1).  `Layout.isSendableDestination` requires `isActive()`, a property of the SQUARE (the
            // menu writes it, `shutTiles` reads it in bulk), and a station switched out of service is not one autonomy
            // will choose.
            boolean shut = Boolean.FALSE.equals(getPointProperty(tile, "active"));

            if (store.isStation(tile) && (shut || !isAutoDestination(tile))) out.add(tile);
        }

        return out;
    }

    /**
     * The stations autonomy leaves alone that a PERSON can still drive to (E8V-B1).
     *
     * `stationsAutonomyWillNotChoose` answers about two different things at once: a station marked manual-only, which is
     * a preference about what autonomy picks, and a station switched OUT OF SERVICE, which is a fact about the railway
     * that binds every tier.  The magenta leg colour was asked for in Adam's own words - *"just use a different color
     * going to manual-only points"* - and a shut square is not one of those: nothing may be sent there at all
     * (`Layout.isPathClear` refuses a closed final point in every tier, and
     * `Layout.isOfferableToOperator` refuses it outright).
     *
     * @return the stations a hand-driven send may still reach and autonomy will not choose
     */
    public Set<TileKey> manualOnlyStations()
    {
        Set<TileKey> out = new LinkedHashSet<>(stationsAutonomyWillNotChoose());

        out.removeAll(shutTiles());

        return out;
    }

    /**
     * The squares where turning round is compulsory.
     * @return
     */
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

    /**
     * Whether trains may turn round on this square.
     *
     * Reads the older keys as well as the current one: a setup authored before the three switches (station, turn round,
     * parking) said the same things as "terminus" and "reversing", which are now DERIVED, and a configuration carrying them
     * would otherwise quietly lose its termini the first time it was rebuilt.  Nothing is rewritten on disk; the first edit
     * to a square writes the new spelling.
     *
     * @param tile
     * @return
     */
    public boolean isTurnAround(TileKey tile)
    {
        if (isMustTurnAround(tile)) return true;

        if (Boolean.TRUE.equals(getPointProperty(tile, AutonomyBuilder.CAN_REVERSE))) return true;

        // a terminus was always "a station where trains turn round" - so on a square that is not a station now it turns
        // nothing round: demoting keeps the flag for when the square is promoted again (GSE-C4)
        if (store.isStation(tile) && Boolean.TRUE.equals(getPointProperty(tile, "terminus"))) return true;

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
     * Takes the layout's own JSON rather than the layout: toJSON is the serialization the legacy path trusted for years, so
     * anything it captures is loadable, and a string can be tested without a control station.  Keyed by tile rather than by
     * name, so a Point renamed between sessions keeps its placements; Points whose names no longer match any tile belong to
     * track that no longer exists, and are dropped silently.
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
        capture(layoutJson, configurationName, false, null);
    }

    /**
     * The same, bringing the window's record of how the railway was built up to date (OB-343): the fold writes every
     * setting the railway has into the setup, so the two then agree about all of them, and the record - what they last
     * agreed on - is the railway's settings as they stand.  See `captureTheRailwaysSettings` for why it matters.
     *
     * @param layoutJson what the running Layout serialized to
     * @param configurationName which configuration this layout's state belongs to
     * @param asBuilt the railway's settings as it was built, changed in place to what the fold wrote; or null
     */
    public void captureFromLayout(String layoutJson, String configurationName, org.json.JSONObject asBuilt)
    {
        capture(layoutJson, configurationName, false, asBuilt);
    }

    /**
     * `captureFromLayout` above, and the placements half of `captureWhatTheRailwayOwns`: each square's train, the side
     * and road of its tail, and the facing of the copy it stands on.
     *
     * @param layoutJson what the running Layout serialized to
     * @param configurationName which configuration this layout's state belongs to
     * @param placementsOnly whether to write where the trains stand and nothing else
     * @param asBuilt the railway's settings as it was built, changed in place to the settings written; or null
     */
    private void capture(String layoutJson, String configurationName, boolean placementsOnly, org.json.JSONObject asBuilt)
    {
        if (layoutJson == null || reducer == null || configurationName == null) return;

        // Not after a rename (MT-135, MT-171, MT-174).  This writes what the running Layout knows back into the
        // configuration, keyed by tile, and works out those keys from THIS session's naming, which a rename has just made
        // stale: the store was rekeyed to the new page name, the graph, the reducer and the page objects still carry the
        // old one.  So every placement would be written a second time under the old name, and a locomotive in two places
        // at once fails the whole setup (MT-135).
        //
        // Refused rather than repaired, and nothing is lost: the rename has already written the store through
        // saveWithoutReconciling, and a rename is refused while autonomy is running, so the running layout knows nothing
        // the store does not.  The session is about to be discarded and rebuilt from the renamed pages in any case.
        if (pagesStale || squaresMoved) return;

        org.json.JSONObject configuration = store.getConfiguration(configurationName);

        if (configuration == null) return;

        org.json.JSONObject root = new org.json.JSONObject(layoutJson);

        // name -> tile, through the same naming the builder used to generate the file
        Map<String, TileKey> tilesByName = new LinkedHashMap<>();

        AutonomyBuilder naming = builder(null);

        tilesByName.putAll(naming.tilesByName());

        // Which way a train standing on each emitted Point is pointing.  See the facing capture below.
        Map<String, TilePorts.Side> facings = naming.facingByName();

        // Every square of the diagram, every page's: what a running Point's square is checked against
        Set<TileKey> diagram = squaresOfTheDiagram();

        org.json.JSONObject points = new org.json.JSONObject();

        if (root.has("points"))
        {
            for (Object o : root.getJSONArray("points"))
            {
                org.json.JSONObject point = (org.json.JSONObject) o;

                // BY THE SQUARE THE RUNNING POINT IS A COPY OF (RSA6-A2), where it names one this diagram has - not by
                // its name through today's naming, which a page ticked out, or a name moved to another square, has
                // changed since the railway was built: the trains on the page were skipped, and kept where the run began.
                TileKey tile = squareOf(point, diagram);

                if (tile == null) tile = tilesByName.get(point.optString("name"));

                if (tile == null) continue;

                org.json.JSONObject extras = new org.json.JSONObject();

                for (String key : placementsOnly ? PLACEMENT_KEYS : POINT_OPERATIONAL_KEYS)
                {
                    if (!point.has(key) || point.isNull(key)) continue;

                    // A placement records WHICH locomotive stands here and nothing else: `Point.toJSON` also writes its length,
                    // reversibility, speed and functions, and parseAuto applies those back onto the Locomotive, so capturing
                    // them would silently revert changes made in the locomotive UI.  Those live in LocDB.
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

                // Which way the train ended up pointing, learned rather than asked for: the copy a locomotive is standing on says
                // which way round it is.  The copy's own facing, where the running layout says it (RSA6-A2); else the
                // naming's, for a name it still gives that square.
                TilePorts.Side facing = sideNamed(point.optString(AutonomyBuilder.COPY_FACING, null));

                if (facing == null && tile.equals(tilesByName.get(point.optString("name"))))
                {
                    facing = facings.get(point.optString("name"));
                }

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

                // An empty one is still recorded, and must be: skipping a square the running layout had NOTHING to say about
                // would never reach the merge's `else remove`, and a locomotive that had driven away from a plain sensor would
                // stay placed there in the configuration.

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

        // Merged per point, not substituted wholesale: the running Layout was built BEFORE any edits made in the editor
        // since, so replacing the whole object would discard them.  What the running layout knows about is overwritten;
        // everything else is left alone.
        org.json.JSONObject existing = configuration.has("points")
            ? configuration.getJSONObject("points") : new org.json.JSONObject();

        // A TRAIN THE RAILWAY CANNOT STAND, kept where the setup records it: its facing has no copy of its square, so the
        // railway does not have it, and folding that square's emptiness in would take the train off the setup too.  Kept
        // unless the railway now has that train somewhere else.
        java.util.Set<TileKey> unheld = configurationName.equals(store.getActiveConfiguration())
            ? facingsThatCannotBeHeld() : java.util.Collections.<TileKey>emptySet();

        java.util.Set<String> capturedTrains = new java.util.HashSet<>();

        for (String id : points.keySet())
        {
            String there = nameOfPlacedLocomotive(points.getJSONObject(id).opt("loc"));

            if (there != null) capturedTrains.add(there);
        }

        for (String id : points.keySet())
        {
            org.json.JSONObject captured = points.getJSONObject(id);
            org.json.JSONObject before = existing.has(id)
                ? existing.getJSONObject(id) : new org.json.JSONObject();

            String recorded = nameOfPlacedLocomotive(before.opt("loc"));

            if (!captured.has("loc") && recorded != null && !capturedTrains.contains(recorded)
                && unheld.contains(AutonomyCompanionStore.parseTileKey(id)))
            {
                continue;
            }

            // AND THE TAIL GOES WITH THE TRAIN THAT LEFT (RGD-B1).  The loop below replaces `loc` from the running layout, so
            // a capture can hand a square to a different locomotive; leaving the OLD train's `arrivedFrom` and `arrivedAlong`
            // on a square whose occupant just changed is the defect the placement doors were fixed for.  Read before the
            // replacement, because the replacement is what makes them differ.  (REG8-A1 fixed the other half: the build
            // applies the side after placing trains, so the file gets the last word.)
            String occupantWas = nameOfPlacedLocomotive(before.opt("loc"));
            String occupantNow = nameOfPlacedLocomotive(captured.opt("loc"));

            if (occupantWas != null && !occupantWas.equals(occupantNow))
            {
                before.remove("arrivedFrom");
                before.remove("arrivedAlong");
            }

            // Keys the layout can speak for are replaced - including being REMOVED when the layout no
            // longer carries them, which is how a property returned to its default is cleared.
            for (String key : placementsOnly ? PLACEMENT_KEYS : POINT_OPERATIONAL_KEYS)
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
            else if (!before.has("home") && !placementsOnly)
            {
                before.remove(AutonomyBuilder.HOME_FACING);
            }

            existing.put(id, before);
        }

        // A square whose TILE is gone keeps nothing.  A square that is merely not a Point keeps everything: a sensor reduces
        // to a Point only where track connects it to something, so a station nudged off its run stops being a Point while
        // remaining on the diagram.  Adam asked that the placement be kept, the build refuse it, and it come back when the
        // track is joined up again.  A disconnected station is a mistake somebody is in the middle of making; the check
        // reports it.  Deleting the tile is still deleting it - the tile is then not in the graph either.
        //
        // (Judged against squares rather than against what this capture had something to say about: a square marked "trains
        // may turn round here" and nothing else carries no operational data, so keying the prune on what was captured would
        // delete the marking the first time autonomy ran.)
        Set<String> stillThere = new LinkedHashSet<>();

        for (TileKey tile : graph.getTiles().keySet()) stillThere.add(tile.toString());

        // Which pages this reduction was even allowed to look at.  A page left out of autonomy has no Points in the
        // reduction, so judging its squares by that reduction condemns every one of them, and excluding a page has to be
        // reversible.  Taken from the layout's own pages, not from the pages the reduction happens to have Points on: a page
        // whose last sensor was deleted has no Points, so inferring the list from the reduction would exempt it forever.
        Set<String> pagesInPlay = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            // NOR A STAND-IN FOR A PAGE THAT WOULD NOT READ (AMS-A1).  It carries the real page's name and one text tile, so
            // judged here every placement, home and priority on that page would be "no longer there" - the MT-135 loss
            // `pagesSafeToJudge` and `save()` refuse since FV3-A1.
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

        // Not where only the trains are written: a square gone is the setup's business (RLV10-B1)
        if (!placementsOnly) for (String id : gone) existing.remove(id);

        configuration.put("points", existing);

        if (placementsOnly)
        {
            dirty = true;

            return;
        }

        // and the top of the file: pace, speeds, and the rest of the settings panel
        org.json.JSONObject globals = globalsOf(root);

        configuration.put("globals", globals);

        // AND THE RECORD OF HOW THE RAILWAY WAS BUILT IS THEN THE RAILWAY'S SETTINGS (OB-343): every one was just written
        // into the setup, so the two agree about all of them.  See `captureTheRailwaysSettings`.
        if (asBuilt != null)
        {
            for (String key : new ArrayList<>(asBuilt.keySet())) asBuilt.remove(key);

            for (String key : globals.keySet()) asBuilt.put(key, copyOfSetting(globals.get(key)));
        }

        dirty = true;
    }

    /**
     * The top of a running layout's file: pace, speeds, the rest of the settings panel, and the timetable.
     */
    public static org.json.JSONObject globalsOf(org.json.JSONObject root)
    {
        org.json.JSONObject globals = new org.json.JSONObject();

        for (String key : root.keySet())
        {
            if (!"points".equals(key) && !"edges".equals(key)) globals.put(key, root.get(key));
        }

        return globals;
    }

    /**
     * Folds what the railway owns, and nothing the setup was given since the railway was built (RSA20-A1): where each
     * train stands - the train, its facing, the side it came in by and its road - and the settings and timetable the
     * Auto tab and a run write to the railway.  The fold made while the setup is newer than the railway: folding all of
     * it wrote the older railway's homes, lengths and priorities over the edit, and folding none of it lost where the run
     * left the trains and every timetable entry it captured.
     *
     * @param layoutJson what the running Layout serialized to
     * @param configurationName which configuration this layout's state belongs to
     * @param asBuilt the railway's settings as it was built, or null to take every one of them
     */
    public void captureWhatTheRailwayOwns(String layoutJson, String configurationName, org.json.JSONObject asBuilt)
    {
        capture(layoutJson, configurationName, true, null);

        captureTheRailwaysSettings(layoutJson, configurationName, asBuilt);
    }

    /**
     * Folds the railway's settings and timetable alone (RSA20-B1): the Auto tab and a run write them to the railway, and a
     * rebuild from the setup throws away whatever had not been folded.
     *
     * **Only those the railway changed since it was built**, where it is told how it was built: a door can edit the
     * setup's own - a timetable put in place, a routing choice - and fold-everything would write the railway's older value
     * over it.  A setting the railway has not touched is the setup's to say.
     *
     * **And the record of how it was built is brought up to date with what is taken (OB-343).**  It is what the railway
     * and the setup last agreed on, and a setting taken here is the railway's and the setup's both.  Left as it was, a fold
     * that no rebuild follows (the editor opening, a load the build declined) leaves the record behind, and a setting the
     * railway then changed back reads as unchanged since it was built.  A door that may put the setup back after a fold
     * keeps the record beside its snapshot of the setup and puts it back with it (`TrainControlUI.noteSettingsAsBuilt`;
     * the old-file import): the record must never say the setup has what a rollback took away.  An edit session's Cancel
     * is the other way round: what a fold takes is the railway's, and Cancel keeps it (`beginEditSession`).
     *
     * **The paused trains are taken BY TRAIN (OB-343), in every fold.**  The railway's list holds the trains a card paused
     * and the trains a tick paused, the tick having written the record as well as the setup; what the railway has against
     * the record is the cards' alone.  So the trains it gained or lost against the record are applied to the setup's own
     * list, and the list is never replaced: a replaced list would put a tick the editor had made, or the setup's own pause
     * of a train, back where Cancel had taken it from.  With no record, every one of the railway's is taken, and the list
     * is the railway's.
     *
     * @param layoutJson what the running Layout serialized to
     * @param configurationName which configuration this layout's state belongs to
     * @param asBuilt the railway's settings as it was built, or null to take every one of them; changed in place to the
     *        settings taken
     */
    public void captureTheRailwaysSettings(String layoutJson, String configurationName, org.json.JSONObject asBuilt)
    {
        if (layoutJson == null || configurationName == null) return;

        org.json.JSONObject configuration = store.getConfiguration(configurationName);

        if (configuration == null) return;

        org.json.JSONObject now = globalsOf(new org.json.JSONObject(layoutJson));

        org.json.JSONObject globals = configuration.optJSONObject("globals");

        if (globals == null)
        {
            globals = new org.json.JSONObject();

            configuration.put("globals", globals);
        }

        for (String key : now.keySet())
        {
            // UNCHANGED ON THE RAILWAY: the setup's stands, edited or not
            if (asBuilt != null && asBuilt.has(key) && sameSetting(asBuilt.get(key), now.get(key))) continue;

            // NOT THE TIMETABLE WHILE THE PAGES ARE STALE (RSA22-C1), as the placements are not (`capture`): a page renamed or
            // squares moved renamed the Points the timetable names in the setup, and the railway still has the old names.
            // The rename's own fold before it, and the editor's on opening, have taken the run's timetable already.
            if ((pagesStale || squaresMoved) && "timetable".equals(key)) continue;

            if (org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES.equals(key))
            {
                // BY TRAIN, against the record before the record follows (OB-343)
                takePausesByTrain(configurationName, globals, asBuilt == null ? null : asBuilt.opt(key), now.get(key),
                    asBuilt == null);
            }
            else
            {
                globals.put(key, now.get(key));

                // what an edit session's Cancel keeps (OB-343)
                takenFromTheRailway(configurationName, key, now.get(key));
            }

            // the railway's and the setup's both now: the record follows (OB-343)
            if (asBuilt != null) asBuilt.put(key, copyOfSetting(now.get(key)));
        }

        // AND ONE THE RAILWAY STOPPED WRITING (RSA21-C1): Simulate and the sequential flag are written only while on, so
        // turning one off left no key to compare - and the setup kept it on
        if (asBuilt != null)
        {
            for (String key : new ArrayList<>(asBuilt.keySet()))
            {
                if (now.has(key)) continue;

                if (org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES.equals(key))
                {
                    // nobody paused any more: every train the record had is a loss (OB-343)
                    takePausesByTrain(configurationName, globals, asBuilt.opt(key), org.json.JSONObject.NULL, false);
                }
                else
                {
                    globals.remove(key);

                    takenFromTheRailway(configurationName, key, org.json.JSONObject.NULL);
                }

                asBuilt.remove(key);
            }
        }

        dirty = true;
    }

    /**
     * Takes the railway's paused trains into the setup's list BY TRAIN: those it gained against the record added, those it
     * lost taken off, and the rest of the list as it was (OB-343; see `captureTheRailwaysSettings`).
     *
     * @param configurationName the configuration folded into
     * @param globals its settings
     * @param recorded the record's list of the paused, or null where there is no record
     * @param value the railway's list, or JSON null where it pauses nobody
     * @param wholesale whether there is no record, so that the list is the railway's own
     */
    private void takePausesByTrain(String configurationName, org.json.JSONObject globals, Object recorded, Object value,
        boolean wholesale)
    {
        Set<String> before = namesIn(recorded);
        Set<String> after = namesIn(value);

        Set<String> gained = new java.util.TreeSet<>(after);
        Set<String> lost = new java.util.TreeSet<>(before);

        gained.removeAll(before);
        lost.removeAll(after);

        if (wholesale) putPausedNames(globals, after);
        else applyPauses(globals, gained, lost);

        if (pausesTakenFromTheRailway == null) return;

        java.util.Map<String, Boolean> pauses = pausesTakenFromTheRailway.get(configurationName);

        if (pauses == null)
        {
            pauses = new java.util.TreeMap<>();

            pausesTakenFromTheRailway.put(configurationName, pauses);
        }

        for (String name : gained) pauses.put(name, Boolean.TRUE);

        for (String name : lost) pauses.put(name, Boolean.FALSE);
    }

    /** The setup's list of the paused with these trains added and those taken off. */
    private static void applyPauses(org.json.JSONObject globals, Set<String> gained, Set<String> lost)
    {
        Set<String> names = namesIn(globals.opt(org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES));

        names.addAll(gained);
        names.removeAll(lost);

        putPausedNames(globals, names);
    }

    /** The setup's list of the paused, as the railway writes it: sorted, and not at all while it names nobody. */
    private static void putPausedNames(org.json.JSONObject globals, Set<String> names)
    {
        if (names.isEmpty()) globals.remove(org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES);
        else globals.put(org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES, new org.json.JSONArray(names));
    }

    /**
     * Notes a setting a fold took from the railway while an edit session is open (see `takenFromTheRailway`).
     *
     * @param configurationName the configuration folded into
     * @param key the setting
     * @param value the railway's value, or JSON null where it stopped writing one
     */
    private void takenFromTheRailway(String configurationName, String key, Object value)
    {
        if (takenFromTheRailway == null) return;

        value = copyOfSetting(value);

        org.json.JSONObject taken = takenFromTheRailway.get(configurationName);

        if (taken == null)
        {
            taken = new org.json.JSONObject();

            takenFromTheRailway.put(configurationName, taken);
        }

        taken.put(key, value);
    }

    /** The names in a list of trains as the setup keeps it, sorted; none for anything else. */
    private static Set<String> namesIn(Object list)
    {
        Set<String> names = new java.util.TreeSet<>();

        if (list instanceof org.json.JSONArray)
        {
            for (Object name : (org.json.JSONArray) list)
            {
                if (name instanceof String) names.add((String) name);
            }
        }

        return names;
    }

    /** A setting's value as a copy, so the record of how the railway was built never shares an array with the setup's. */
    private static Object copyOfSetting(Object value)
    {
        if (value instanceof org.json.JSONObject) return new org.json.JSONObject(value.toString());

        if (value instanceof org.json.JSONArray) return new org.json.JSONArray(value.toString());

        return value;
    }

    /** Whether two values of a setting are the same, as the railway writes them. */
    private static boolean sameSetting(Object a, Object b)
    {
        if (a instanceof org.json.JSONObject && b instanceof org.json.JSONObject)
        {
            return ((org.json.JSONObject) a).similar(b);
        }

        if (a instanceof org.json.JSONArray && b instanceof org.json.JSONArray)
        {
            return ((org.json.JSONArray) a).similar(b);
        }

        return String.valueOf(a).equals(String.valueOf(b));
    }

    /**
     * The squares with names of their own on pages out of autonomy, and those names (RSA6-C1): they take their turn in
     * the naming though they are not built, so a name shared with a page in play is settled as if they were.
     */
    private Map<TileKey, String> namesOutOfPlay()
    {
        Set<String> out = new java.util.HashSet<>(store.getExcludedPages());

        if (out.isEmpty()) return Collections.emptyMap();

        Map<TileKey, String> names = new LinkedHashMap<>();

        for (LayoutDiagram page : pages)
        {
            if (!out.contains(page.getName())) continue;

            for (LayoutDiagramComponent component : page.getAll())
            {
                TileKey tile = new TileKey(page.getName(), component.getX(), component.getY());

                String name = store.getPointName(tile);

                if (name != null && !name.trim().isEmpty()) names.put(tile, name);
            }
        }

        return names;
    }

    /**
     * The square of the diagram a running Point says it is a copy of, where the diagram has it, or null (RSA6-A2).
     *
     * @param point a Point as the running layout writes it
     * @param diagram every square of the diagram
     * @return its square, or null
     */
    private static TileKey squareOf(org.json.JSONObject point, Set<TileKey> diagram)
    {
        String square = point.optString("square", null);

        TileKey tile = square == null ? null : AutonomyCompanionStore.parseTileKey(square);

        return tile != null && diagram.contains(tile) ? tile : null;
    }

    /**
     * Every square of the diagram, on every page - those out of autonomy too, which the graph leaves out.
     */
    private Set<TileKey> squaresOfTheDiagram()
    {
        Set<TileKey> out = new java.util.HashSet<>();

        for (LayoutDiagram page : pages)
        {
            for (LayoutDiagramComponent component : page.getAll())
            {
                out.add(new TileKey(page.getName(), component.getX(), component.getY()));
            }
        }

        return out;
    }

    /**
     * Clears the timetable of the configuration in use, every entry of it (RSA5-C1).  Saved.
     *
     * @return whether there was a configuration to clear
     * @throws IOException if the setup cannot be written
     */
    public boolean clearTheTimetable() throws IOException
    {
        return setGlobal("timetable", new org.json.JSONArray());
    }

    /**
     * Writes one run-wide setting into the active configuration and saves.
     *
     * TARGETED, because the whole-layout route is wrong for this: captureFromLayout copies everything Layout.toJSON knows,
     * including where every train is standing, and the window's wrapper declines to run while autonomy is running.
     * Choosing a routing rule should not record the position of every train as a side effect, nor be quietly ignored
     * mid-session.  Saved immediately, because a setting the user picked from a menu and did not see written is one they
     * will pick again.
     *
     * ANSWERS WHETHER IT STORED, because two of its three ways of doing nothing are silent (LE-B5): a caller that deletes
     * its own copy of a setting on the strength of having called this needs to know.
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
     * The trains this configuration pauses (FR-117; Adam: "in autonomy configs, track the paused/unpaused
     * status of locomotives, as designated on the autonomy locomotive controls tab"), by name and sorted.
     *
     * Kept with the configuration's settings, under the key the railway writes them under (`Layout.PAUSED_LOCOMOTIVES`),
     * so the build carries them to the railway as it carries the rest, and the fold brings back a pause made there - the
     * Auto tab's pause button, pressed while trains run, when the setup is not edited.
     *
     * @return the names; empty where no configuration is active or it pauses nobody
     */
    public java.util.Set<String> getPausedLocomotives()
    {
        java.util.Set<String> names = new java.util.TreeSet<>();

        String active = store.getActiveConfiguration();

        org.json.JSONObject configuration = active == null ? null : store.getConfiguration(active);

        org.json.JSONObject globals = configuration == null ? null : configuration.optJSONObject("globals");

        org.json.JSONArray list = globals == null ? null
            : globals.optJSONArray(org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES);

        for (int i = 0; list != null && i < list.length(); i++)
        {
            Object name = list.opt(i);

            if (name instanceof String && !((String) name).trim().isEmpty()) names.add((String) name);
        }

        return names;
    }

    /**
     * Records a train paused, or set going, in the active configuration, and saves it (FR-117).  The list is written only
     * while it names somebody, as the railway writes it, so a configuration that pauses nobody carries no key.
     *
     * @param name the train
     * @param paused whether it is paused
     * @return whether there was a configuration to write it to
     * @throws IOException if the setup cannot be written
     */
    public boolean setLocomotivePaused(String name, boolean paused) throws IOException
    {
        if (name == null) return false;

        return setLocomotivesPaused(java.util.Collections.singletonList(name), paused);
    }

    /**
     * Records several trains paused, or set going, in the active configuration, and saves it ONCE (FR-126).  The record
     * `setLocomotivePaused` keeps for one train, written for the bulk tools' many in a single save.
     *
     * @param names the trains
     * @param paused whether they are paused
     * @return whether there was a configuration to write it to
     * @throws IOException if the setup cannot be written
     */
    public boolean setLocomotivesPaused(java.util.Collection<String> names, boolean paused) throws IOException
    {
        java.util.Set<String> listed = getPausedLocomotives();

        for (String name : names)
        {
            if (name == null) continue;

            if (paused) listed.add(name);
            else listed.remove(name);
        }

        return setGlobal(org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES,
            listed.isEmpty() ? null : new org.json.JSONArray(listed));
    }

    /**
     * Pauses a train or sets it going (FR-117): the train itself, which autonomy and Return Home read, and this
     * configuration's record of it, saved - the two together, so a fold between them cannot take one for the other.
     *
     * @param train the train
     * @param active whether autonomy may run it
     * @throws IOException if the setup cannot be written
     */
    public void setActive(Locomotive train, boolean active) throws IOException
    {
        if (train == null) return;

        setTrainsActive(java.util.Collections.singletonList(train), active);
    }

    /**
     * Pauses every one of these trains or sets every one going (FR-126), as `setActive` does for one: each train itself,
     * and this configuration's record of them all, saved once.  The model call is the same, `setAutonomyPaused`, per train.
     *
     * @param trains the trains
     * @param active whether autonomy may run them
     * @throws IOException if the setup cannot be written
     */
    public void setTrainsActive(java.util.Collection<Locomotive> trains, boolean active) throws IOException
    {
        java.util.List<String> names = new java.util.ArrayList<>();

        for (Locomotive train : trains)
        {
            if (train == null) continue;

            train.setAutonomyPaused(!active);

            names.add(train.getName());
        }

        setLocomotivesPaused(names, !active);
    }

    /**
     * What the active configuration STORES for a global, or null (RC-A2).
     *
     * The counterpart setGlobal never had.  "Has the configuration already answered this?" cannot be put to the live
     * Layout, which carries whatever was last pushed into it in memory, so a migration that writes the value and asks
     * again is told yes by its own writing.  Null covers all three ways of having no answer - no active configuration, no
     * configuration object, no such global.
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
     * Here rather than in the menu that asks it, so the rule has a name, one home, and a test.  Making a link one-way
     * needs the JUMP to carry the direction (on the backlog); this returns false until it does.
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
     * The facing says which way the train standing there points, and the build honours it by choosing the copy of the
     * split square that faces that way.  When no copy does, the builder falls back to the first copy trains may arrive at
     * (`startableCopy`), which may point the other way, so the train quietly turns round.  Squares stop being able to hold
     * a facing when the track around them changes (move a tile so that it is entered from different sides) while the
     * recorded one stays as it was.
     *
     * Reported rather than corrected: the train is physically pointing whichever way it is pointing, and only the
     * operator knows.  A square with NO facings is left out - that is a disconnected square, which has its own findings.
     */
    private java.util.Set<TileKey> facingsThatCannotBeHeld()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        // WHERE THE RAILWAY HAS THE TRAIN, as the warning and the Facing menu ask it (RSA17-B1)
        Map<String, Object[]> railway = railwayTrains();

        for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())
        {
            // A RECORD THE LAST RUN HAS OUTRUN is no train there (RSA17-B1): the railway has it standing on another square,
            // and the railway wins (OB-183) - an error about a square no train stands on stopped every send
            Object[] there = railway.get(placed.getValue());

            if (there != null && !placed.getKey().equals(there[0])) continue;

            // AND ON THE SAME SQUARE, THE WAY THE RAILWAY HAS IT FACING (RSA18-B1): a run can turn a train and bring it back,
            // and the setup's facing is then the one it set off with
            Side recorded = there != null && there[1] != null ? (Side) there[1] : getFacing(placed.getKey());

            if (recorded == null) continue;

            List<Side> offered = facingChoices(placed.getKey());

            if (offered.isEmpty() || offered.contains(recorded)) continue;

            // A TRAIN THAT TURNED ROUND is simply offered, not excused: on a square a train may turn round on, the facing a
            // turning copy holds IS an arrival side, and `facingChoices` ("which facings can this square hold") includes it
            // (OB-145), so the test above is true by construction.  An excuse here would forgive a facing the menu would not
            // offer, and make the disagreement invisible from either side.
            out.add(placed.getKey());
        }

        return out;
    }

    /**
     * The stations whose paired protecting signal no longer resolves to an accessory.
     *
     * The same walk protectingSignalNames does, keeping what it skips.  It has to skip them - a build cannot emit an
     * accessory that is not there - but skipping in silence is what leaves an operator believing a platform is protected
     * when it is not.
     */
    private java.util.Set<TileKey> signalsThatAreGone()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        if (graph == null) return out;

        // ANY of them, not all: a station paired to two signals of which one has gone is protected on
        // one approach and not on the other, which is exactly the state worth warning about.
        //
        // BOTH GUARDS (FR-096): an entry-guard signal that has gone is dropped from the build the same way, and the
        // way into that station is then as unguarded as a platform whose protecting signal went.
        // STATIONS' (GSE-C4): a square that is not one now keeps its guards, and nothing throws them
        Map<TileKey, List<TileKey>> both = new LinkedHashMap<>(stationsOnly(store.getProtectingSignals()));

        for (Map.Entry<TileKey, List<TileKey>> entry : stationsOnly(store.getEntrySignals()).entrySet())
        {
            List<TileKey> merged = new ArrayList<>(both.containsKey(entry.getKey())
                ? both.get(entry.getKey()) : java.util.Collections.<TileKey>emptyList());

            for (TileKey signal : entry.getValue()) if (!merged.contains(signal)) merged.add(signal);

            both.put(entry.getKey(), merged);
        }

        for (Map.Entry<TileKey, List<TileKey>> pair : both.entrySet())
        {
            // NOT ABOUT A PAGE THAT IS SWITCHED OFF (SVN-C6): `getProtectingSignals()` is the raw store, and the graph does
            // not carry an excluded page, so a pairing whose station sits on one would report its signal as GONE.
            if (store.getExcludedPages().contains(pair.getKey().getPage())) continue;

            // THE SIGNAL SIDE IS NOT FILTERED, and that asymmetry is the point (REL-B1): a pairing whose STATION is on an
            // excluded page is not in play, so a warning is noise.  A signal on an excluded page is dropped from the built
            // configuration (`protectingSignalNames` resolves through `graph.getTiles()`), so the platform really IS
            // unprotected; silencing that warning would be the one direction this check must never fail in.
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
     * The setting "may change direction here" offers a choice only if a train that has arrived could instead CARRY ON,
     * leaving by some side other than the one it came in by.  True only when that is impossible from every direction:
     * one arrival with somewhere to go makes the setting mean what it says, and a warning would then tell the operator
     * their railway is something it is not.  Departures are gathered the way `departureSides` gathers them, as the
     * trapped-arrival check does, so the two spellings cannot disagree.
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
     * **One walk for what four methods spelled out** (`homeTiles`, `tilesWithAHome`, `homesElsewhere`, `shutTiles`): they
     * have to agree, or a qualification added to one (homes on excluded pages, squares the diagram no longer draws) makes
     * the bulk clear act on a set the findings never mentioned.
     *
     * The parse splits on the LAST colon, because a page name may hold one; that rule lives on the store.
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
     * The findings of the last check, kept for the rest of the event that asked for them.
     * One click on a switch in the autonomy editor asked for them twenty times over five events, and every check builds the
     * whole configuration to inspect it.  Kept for ONE EVENT: a new event always checks afresh, and within the event only
     * while everything the check reads is as it was (whatTheCheckReads), so a gesture that changes the setup, a train, a
     * length or a label and then asks again is answered for the change.  Off the event thread nothing is kept.
     */
    private java.lang.ref.WeakReference<java.awt.AWTEvent> findingsEvent;

    /** What the check read when the kept findings were worked out. */
    private List<Object> findingsKey;

    /** The kept findings; copied out, so each caller's list is its own. */
    private List<AutonomyChecks.Finding> findingsKept;

    /**
     * Everything check() reads that can change, as a key that is equal only while all of it is as it was.
     *
     * The graph, its reduction and the station index, each replaced whole when it changes; the setup, as its snapshot
     * gives it; the running railway, the trains on it and those the setup places, with each one's length; and every
     * square of every page as the diagram has it now, whose labels and signal names the check reads straight off it.
     *
     * @return the key
     */
    private List<Object> whatTheCheckReads()
    {
        List<Object> key = new ArrayList<>();

        key.add(new Same(graph));
        key.add(new Same(reducer));
        key.add(new Same(stationIndex));
        key.add(store.snapshotSetup().toString());

        org.traincontrol.automation.Layout running = runningLayout == null ? null : runningLayout.get();

        key.add(new Same(running));

        StringBuilder trains = new StringBuilder();

        for (Map.Entry<String, Object[]> train : railwayTrains().entrySet())
        {
            trains.append(train.getKey()).append(Arrays.deepToString(train.getValue())).append('\n');
        }

        for (Map.Entry<TileKey, String> standing : trainsWhereTheyStand().entrySet())
        {
            trains.append(standing.getKey()).append(' ').append(standing.getValue()).append('=')
                .append(trainLengths == null ? null : trainLengths.apply(standing.getValue())).append('\n');
        }

        key.add(trains.toString());

        StringBuilder squares = new StringBuilder();

        for (LayoutDiagram page : pages)
        {
            key.add(new Same(page));

            for (LayoutDiagramComponent square : page.getAll())
            {
                squares.append(square.getX()).append(',').append(square.getY()).append(' ').append(square.getType())
                    .append(' ').append(square.getOrientation()).append(' ').append(square.getState()).append(' ')
                    .append(square.getRawAddress()).append(' ').append(square.getLabel()).append(' ')
                    .append(square.getAccessory() == null ? null : square.getAccessory().getName()).append('\n');
            }
        }

        key.add(squares.toString());

        return key;
    }

    /** An object in a key by identity: the same one, not an equal one. */
    private static final class Same
    {
        private final Object what;

        Same(Object what)
        {
            this.what = what;
        }

        @Override
        public boolean equals(Object other)
        {
            return other instanceof Same && ((Same) other).what == what;
        }

        @Override
        public int hashCode()
        {
            return System.identityHashCode(what);
        }
    }

    /**
     * Everything wrong or worth knowing about the setup as it stands.
     * @return
     */
    public List<AutonomyChecks.Finding> check()
    {
        // KEPT FOR THE REST OF THE EVENT, while nothing it read has changed - see findingsEvent
        java.awt.AWTEvent event = java.awt.EventQueue.isDispatchThread() ? java.awt.EventQueue.getCurrentEvent() : null;

        if (event == null || graph == null || reducer == null) return checkAfresh();

        List<Object> key = whatTheCheckReads();

        if (findingsEvent == null || findingsEvent.get() != event || !key.equals(findingsKey))
        {
            List<AutonomyChecks.Finding> found = checkAfresh();

            findingsEvent = new java.lang.ref.WeakReference<>(event);
            findingsKey = key;
            findingsKept = found;
        }

        return new ArrayList<>(findingsKept);
    }

    /**
     * The check itself, worked out every time it is asked.
     *
     * @return the findings, most severe first
     */
    private List<AutonomyChecks.Finding> checkAfresh()
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
            // A STATION'S, as `isTurnAround` reads the same flag (GSE-C4)
            if (store.isStation(tile) && Boolean.TRUE.equals(getPointProperty(tile, "terminus"))) termini.add(tile);
        }

        // "May turn round here" where no arriving train could do anything else (OB-123).  Adam: "this is wrong:
        // the train can continue or reverse.  It should test for two outgoing paths, not two incoming."  Carrying on is a
        // DEPARTURE, so a square with one way in and two ways out offers every arriving train the choice the setting
        // describes.  Per ARRIVAL rather than by counting departures: two arrivals and one departure forces a turn on the
        // train that arrived by the departure side and offers a genuine choice to the other, so the setting is pointless
        // only when EVERY arrival is a forced turn.  Arrivals come from arrivalSides rather than a walk of our own,
        // because the answer has to be the BUILD's (DR-B6/DD-A7).
        Set<TileKey> pointless = new LinkedHashSet<>();

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (!isTurnAround(tile) || isMustTurnAround(tile)) continue;

            if (everyArrivalMustTurn(graph, reducer, tile)) pointless.add(tile);
        }

        // Built once, up here, because the checks below are ABOUT the build (DD-A7).  `AutonomyChecks` is a predictor, not a
        // second authority: where the build can answer, its answer is the right one and a second derivation can only
        // disagree.  The line is drawn by severity: an ERROR says the setup will not build at all, so there is no build to
        // inspect and those checks reason about the diagram; everything softer describes a railway that WILL exist, and
        // asks it.
        org.json.JSONObject inspected = builtForInspection();

        java.util.Map<String, TileKey> namesForInspection =
            inspected == null ? null : builder(null).tilesByName();

        // which routes command each guard, off the same build (RSA42)
        GuardNotices guards = guardNotices(inspected, namesForInspection);

        // and the side each copy's trains come in by, which a trapped arrival is named by (OB-201)
        java.util.Map<String, Side> arrivalsForInspection =
            inspected == null ? null : builder(null).arrivalByName();

        // Squares a train can reach and then not leave: it arrived by one side, the only way on is back out of that same
        // side, and nobody has said trains may turn round there.  Read off the built graph rather than walked here, so
        // there is one answer.  Nothing is reported when the setup will not build: that has louder problems, all already
        // on the list.
        Map<TileKey, Set<Side>> trapped = tilesWithATrappedArrival(inspected, namesForInspection, arrivalsForInspection);

        // Captions the user's own writing is sitting under: the grid draws the caption and hides the text (MT-017), and the
        // editor asks before it puts a caption over text, naming the text.  Worth saying rather than silently losing.
        Map<TileKey, TileKey> covered = new LinkedHashMap<>();

        for (Map.Entry<TileKey, TileKey> caption : store.getCaptions().entrySet())
        {
            // NOT ONE KEPT FOR A SQUARE THAT IS NOT A STATION NOW (GSE-C4): it is not drawn, so nothing covers it
            if (caption.getValue() == null || !store.isStation(caption.getValue())) continue;

            LayoutDiagram page = pageOf(caption.getKey());

            if (page == null) continue;

            LayoutDiagramComponent component =
                page.getComponent(caption.getKey().getX(), caption.getKey().getY());

            if (component == null || component.getLabel() == null) continue;

            if (!component.getLabel().trim().isEmpty()) covered.put(caption.getKey(), caption.getValue());
        }


        // The run-in notices and the berths given no room, from one walk (TDA4-C2).
        java.util.Set<TileKey> givenNoRoom = new LinkedHashSet<>();

        java.util.Map<TileKey, int[]> runIns = runInFigures(givenNoRoom, inspected, namesForInspection);

        List<AutonomyChecks.Finding> found = AutonomyChecks.run(graph, reducer, termini, getLabelledStationTiles(), pointless,
            // with the squares where closing their trapped sides is something the arrivals menu can do (RSA37-B1)
            trapped, arrivalsClosableAt(trapped), covered, placedLocomotives(), shutStations(),
            mayTurnTiles(), mandatoryTurnTiles(), homeTiles(), signalsThatAreGone(),
            stationsWithNoSignal(), facingsThatCannotBeHeld(),
            // The red arrows, so the findings walk the railway a train can actually use (OB-120).
            barredArrivals(),
            // And the squares switched out of service, which is a different claim: a barred side is
            // passed through and not stopped at, a closed square is not passed through at all
            // (V31-C3).
            shutTiles(),
            // The two halves of the length rule (FR-046), and the two of them disagreeing about one
            // platform.
            placedTrainsWithoutLength(), stationsWithoutMaxLength(), runIns, givenNoRoom,
            // AND THE HALF-MEASURED APPROACH, which closes a berth to everything until it is finished
            // (Adam, on RTX-C2: *"we want clear warnings to the user"*).
            stationsWithAHalfMeasuredApproach(),
            // A terminus with two ways in, which is a statement that cannot be true (MT-361).
            terminiWithTwoWaysIn(),
            // Pages sharing one sensor with another, which cannot be modelled at all (OB-150).
            repeatedSensorPages(),
            // Squares trains reverse at that nobody has measured.
            reversalsWithoutLength(),
            // Stations autonomy will never choose, so a pocket holding only those still leads
            // nowhere (V36-C4).
            stationsAutonomyWillNotChoose(),
            // And the termini a route can back a train into, off the same build
            berthsATrainCanBackInto(inspected, namesForInspection),
            // Copies a train could be sent to and never leave, and copies nothing can reach at all.  Per COPY, which is
            // what every other check here cannot see.
            // ONE BUILD FOR THE THREE OF THEM (D3F-C6): each building the configuration for itself would make one `check()`
            // three full graph builds, on the event thread, every time somebody right-clicks a station.
            destinationCopiesWithNoWayOut(inspected, namesForInspection),
            destinationCopiesWithNoWayIn(inspected, namesForInspection),
            destinationCopiesReachingNoStation(inspected, namesForInspection),
            // A station's two guards as one signal, and the guards the routes that command them say are misplaced (AUT-C2;
            // RSA41-C2, -C3; RSA42-C1, -C2).
            guardsOnBothLists(), guards.entryOffTheWayIn, guards.exitOnNoRoute, guards.exitCrossed, guards.exitOnTheWayIn,
            // Every square unavailable while another is occupied, station or not.
            restrictionsToList());

        // AND WHAT A REFUSAL USED TO STOP, said instead: a train facing track that runs only towards it,
        // and a station at the end of a line trains may not turn at
        found.addAll(AutonomyChecks.checkTrainsFacingTrackTowardsThem(reducer, trainsFacingTrackTowardsThem()));
        java.util.Set<TileKey> endsOfLines = stationsAtTheEndOfALine();

        found.addAll(AutonomyChecks.checkStationsAtTheEndOfALine(reducer, endsOfLines));

        // AND A STATION WHOSE NAME IS DRAWN IN MORE THAN ONE PLACE, which no door of the editor makes (OB-341)
        found.addAll(AutonomyChecks.checkStationsNamedTwice(stationsShownMoreThanOnce()));

        // AND A RUN WHOSE SQUARES POINT OPPOSITE WAYS, which no train can pass and the correction leaves as it is (OB-361)
        found.addAll(AutonomyChecks.checkRunsPointingOppositeWays(runsPointingOppositeWays()));

        // AND A SENSOR ADDRESS THAT TWO PLACES SHARE, which the build cannot tell apart (FR-139)
        found.addAll(AutonomyChecks.checkSensorAddressesShared(sensorAddressesSharedByPlaces()));

        // ONE FINDING AND ONE PIECE OF ADVICE for a station at the end of a line (RSA17-C1): the warning that trains cannot
        // turn there said "may" where the error says "must", and "may" then drew a notice saying "must"
        found.removeIf(finding -> AutonomyChecks.isArrivalTrapped(finding.getMessageKey())
            && endsOfLines.contains(finding.getTile()));

        AutonomyChecks.bySeverity(found);

        return found;
    }

    /**
     * The runs of plain track whose squares point opposite ways (`TileGraph.runsPointingOppositeWays`), each first square
     * to its squares - not on track no station is on, where no train is sent (OB-353, OB-361).
     *
     * @return the runs
     */
    private Map<TileKey, List<TileKey>> runsPointingOppositeWays()
    {
        Map<TileKey, List<TileKey>> out = new LinkedHashMap<>();

        if (graph == null) return out;

        for (Map.Entry<TileKey, Run> run : graph.runsPointingOppositeWays().entrySet())
        {
            if (!reachesNoStation(run.getKey())) out.put(run.getKey(), run.getValue().getTiles());
        }

        return out;
    }

    /**
     * Every station whose name the track diagram draws in more than one place, against the squares it draws it on (OB-341).
     * What counts is what is DRAWN (`drawnCaptionsFor`, the rule the diagram uses), and PER PAGE (Adam: *"The
     * duplicate caption check should be per page.  It is OK to show on another page what is going on on another."*): two
     * captions beside the station on one page are two names on that page, and are the finding; one on its own square and
     * one beside it is drawn once, and one beside it on each of two pages is not here either.  Every door of the editor
     * keeps one station to one caption (OB-256), so what this finds came in past them: a setup file edited by hand, or
     * written by an older version.
     *
     * Stale state is not counted: a caption kept for a square that is no station now is not drawn (GSE-C4), nor is one on a
     * page that is gone or left out of autonomy, nor one of a station unnamed and out of service.  A station that is no
     * Point is counted, since the diagram draws its captions all the same.  In reading order, so the sentence and the list
     * do not change from one load of the file to the next.
     *
     * @return each such station's square, to the squares of the pages that draw its name more than once
     */
    private Map<TileKey, List<TileKey>> stationsShownMoreThanOnce()
    {
        java.util.Comparator<TileKey> readingOrder = java.util.Comparator.comparing(TileKey::getPage)
            .thenComparingInt(TileKey::getY).thenComparingInt(TileKey::getX);

        List<TileKey> stations = new ArrayList<>(new LinkedHashSet<>(store.getCaptions().values()));

        stations.sort(readingOrder);

        Map<TileKey, List<TileKey>> out = new LinkedHashMap<>();

        for (TileKey station : stations)
        {
            // Drawn is drawn: a station the railway built no Point for, on a sensor with no track or on a page left out, has its
            // captions drawn all the same (OB-341).  `drawnCaptionsFor` is empty for a square that is no station.
            // The drawn squares of each page, and the pages that draw the name more than once
            Map<String, List<TileKey>> onPage = new LinkedHashMap<>();

            for (TileKey square : drawnCaptionsFor(station))
            {
                onPage.computeIfAbsent(square.getPage(), k -> new ArrayList<>()).add(square);
            }

            List<TileKey> twice = new ArrayList<>();

            for (List<TileKey> squares : onPage.values())
            {
                if (squares.size() > 1) twice.addAll(squares);
            }

            if (twice.isEmpty()) continue;

            twice.sort(readingOrder);

            out.put(station, twice);
        }

        return out;
    }

    /**
     * Every square held back while another is occupied, against the names of what it watches (Adam: *"a
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
    private Map<TileKey, Map<TileKey, String>> guardsOnBothLists()
    {
        Map<TileKey, Map<TileKey, String>> out = new LinkedHashMap<>();

        if (graph == null) return out;

        // STATIONS' (GSE-C4), as `signalsThatAreGone`
        Map<TileKey, List<TileKey>> exits = stationsOnly(store.getProtectingSignals());

        for (Map.Entry<TileKey, List<TileKey>> entry : stationsOnly(store.getEntrySignals()).entrySet())
        {
            if (store.getExcludedPages().contains(entry.getKey().getPage()) || !exits.containsKey(entry.getKey())) continue;

            for (TileKey signal : entry.getValue())
            {
                if (exits.get(entry.getKey()).contains(signal))
                {
                    out.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>()).put(signal, signalName(signal));
                }
            }
        }

        return out;
    }

    /**
     * The guard notices, station square to the names of its guards, one map per notice (AUT-C2; RSA41-C2, -C3; RSA42-C1,
     * -C2).
     */
    private static final class GuardNotices
    {
        /** Entry guards no route into their station commands. */
        final Map<TileKey, Map<TileKey, String>> entryOffTheWayIn = new LinkedHashMap<>();

        /** Exit guards no route commands at all. */
        final Map<TileKey, Map<TileKey, String>> exitOnNoRoute = new LinkedHashMap<>();

        /** Exit guards routes to other stations command. */
        final Map<TileKey, Map<TileKey, String>> exitCrossed = new LinkedHashMap<>();

        /** Exit guards a route into their station commands. */
        final Map<TileKey, Map<TileKey, String>> exitOnTheWayIn = new LinkedHashMap<>();
    }

    /**
     * Which routes command each guard, read off the built railway (RSA42-C1, -C2).
     *
     * Adam (AUT-C2): *"if the guard signal is not on a path leading to the chosen station, we can add notice to
     * the autonomy editor."*  The BUILT railway is read, not the reduction, because the reduction knows a square but not
     * which way a train faces on it: the built edges have each copy of a point and the accessories each edge commands,
     * which is what turns a guard green.  So the ways in are the built edges walked back from the station's stopping
     * copies to the first stop on each, and the ways out walked on from any copy of it to the first stop.
     *
     * - An ENTRY guard turns red behind a train arriving: noticed where no route into its station commands it.
     * - An EXIT guard is red while its station is occupied, and a route over it sets it green as it sets any signal on it.
     *   Commanded only by routes out of its station, it is that station's exit signal and right.  A route INTO it - a home
     *   signal - turns it green for the arriving train, and the arrival's claim can turn it red in front of that train
     *   (RSA42-C1): the entry guard is the signal on the way in.  Routes to other stations turn it green over a train
     *   standing there (RSA41-C2).  And no route at all, away from the station's own track, leaves it only showing whether
     *   the station is occupied.  One notice for each, in that order.
     *
     * A station on a page left out of autonomy is not in play, and a signal whose tile has gone is `signalsThatAreGone`'s.
     * With no build - the setup has an error - there is nothing to read, and no guard notice.
     *
     * @param built the configuration as built for the check, or null
     * @param named the builder's own map of its names to their squares, or null
     * @return the notices
     */
    private GuardNotices guardNotices(org.json.JSONObject built, Map<String, TileKey> named)
    {
        GuardNotices out = new GuardNotices();

        if (graph == null || built == null || named == null || !built.has("points") || !built.has("edges")) return out;

        Map<String, Boolean> stops = new java.util.HashMap<>();

        org.json.JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.getJSONObject(i);

            stops.put(point.getString("name"), point.optBoolean("station", false));
        }

        org.json.JSONArray edges = built.getJSONArray("edges");

        String[] from = new String[edges.length()];
        String[] to = new String[edges.length()];
        List<java.util.Set<String>> commands = new ArrayList<>();

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONObject edge = edges.getJSONObject(i);

            from[i] = edge.getString("start");
            to[i] = edge.getString("end");

            java.util.Set<String> accessories = new java.util.HashSet<>();

            org.json.JSONArray said = edge.optJSONArray("commands");

            for (int j = 0; said != null && j < said.length(); j++) accessories.add(said.getJSONObject(j).getString("acc"));

            commands.add(accessories);
        }

        java.util.Set<TileKey> stations = new LinkedHashSet<>(store.getEntrySignals().keySet());

        stations.addAll(store.getProtectingSignals().keySet());

        for (TileKey station : stations)
        {
            if (store.getExcludedPages().contains(station.getPage())) continue;

            // NOR ONE THAT IS NOT A STATION NOW (GSE-C4): it keeps its guards, and nothing throws them
            if (!store.isStation(station)) continue;

            java.util.Set<Integer> in = waysAt(station, true, from, to, named, stops);
            java.util.Set<Integer> outOf = waysAt(station, false, from, to, named, stops);

            // A STATION NO ROUTE REACHES OR LEAVES is cut off, and the check says so in its own words (a station at the end
            // of a line, an isolated point) - its guards are not where the trouble is
            if (in.isEmpty() && outOf.isEmpty()) continue;

            for (TileKey signal : nonNull(store.getEntrySignals().get(station)))
            {
                if (!graph.getTiles().containsKey(signal)) continue;

                if (!commandedBy(accessoryOf(signal), in, commands))
                {
                    out.entryOffTheWayIn.computeIfAbsent(station, k -> new LinkedHashMap<>()).put(signal, signalName(signal));
                }
            }

            for (TileKey signal : nonNull(store.getProtectingSignals().get(station)))
            {
                if (!graph.getTiles().containsKey(signal)) continue;

                String accessory = accessoryOf(signal);

                // ELSEWHERE: neither on a way in or out, nor through the station itself - a route over the station's own
                // square cannot be set while a train stands there, so it never turns the guard green over one
                java.util.Set<Integer> elsewhere = new java.util.HashSet<>();

                for (int i = 0; i < commands.size(); i++)
                {
                    if (!in.contains(i) && !outOf.contains(i) && !station.equals(named.get(from[i]))
                        && !station.equals(named.get(to[i]))) elsewhere.add(i);
                }

                // ON THE WAY IN, a train coming in passes it (OB-361): not where the way in starts, leaving a station this
                // signal is the exit guard of - there a train passes it going out, as that station's own exit
                java.util.Set<Integer> comingIn = new java.util.HashSet<>();

                for (Integer i : in)
                {
                    TileKey leaving = named.get(from[i]);

                    if (leaving == null || !store.isStation(leaving)
                        || !nonNull(store.getProtectingSignals().get(leaving)).contains(signal)) comingIn.add(i);
                }

                // NO ROUTE AT ALL is noticed only away from the station's own track: a signal there - past a terminus's sensor, at
                // its buffers - shows whether the station is occupied, and that is what it is for
                Map<TileKey, Map<TileKey, String>> notice = commandedBy(accessory, comingIn, commands) ? out.exitOnTheWayIn
                    : commandedBy(accessory, elsewhere, commands) ? out.exitCrossed
                    : !commandedBy(accessory, outOf, commands) && !onItsOwnTrack(station, signal) ? out.exitOnNoRoute
                    : null;

                if (notice != null) notice.computeIfAbsent(station, k -> new LinkedHashMap<>()).put(signal, signalName(signal));
            }
        }

        return out;
    }

    /**
     * The built edges on the ways into a station or out of it: walked back from its stopping copies, or on from any copy
     * of it, through every point a train passes - a plain sensor, or a copy of a station that bars it - to the first stop on
     * each way (RSA42-C2).  A copy is a point and a facing, so a walk turns back only where the railway lets a train.
     *
     * @param station the station's square
     * @param in true for the ways in, false for the ways out
     * @param from each built edge's start
     * @param to each built edge's end
     * @param named the builder's names to their squares
     * @param stops each copy's name to whether a train stops there
     * @return the indices of the edges on those ways
     */
    private static java.util.Set<Integer> waysAt(TileKey station, boolean in, String[] from, String[] to,
        Map<String, TileKey> named, Map<String, Boolean> stops)
    {
        java.util.Set<Integer> found = new java.util.HashSet<>();
        java.util.Deque<String> toVisit = new java.util.ArrayDeque<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        for (Map.Entry<String, TileKey> copy : named.entrySet())
        {
            if (!station.equals(copy.getValue())) continue;

            // INTO a copy a train stops at; out of any copy, a train passing included
            if (in && !Boolean.TRUE.equals(stops.get(copy.getKey()))) continue;

            if (seen.add(copy.getKey())) toVisit.add(copy.getKey());
        }

        while (!toVisit.isEmpty())
        {
            String at = toVisit.poll();

            for (int i = 0; i < from.length; i++)
            {
                if (!at.equals(in ? to[i] : from[i])) continue;

                found.add(i);

                String next = in ? from[i] : to[i];

                if (!Boolean.TRUE.equals(stops.get(next)) && seen.add(next)) toVisit.add(next);
            }
        }

        return found;
    }

    /**
     * Whether a signal lies on the track a station's own reduced edges run over - the last stretch into it or the first out,
     * its room past the sensor included - and no further, so nothing is walked that a train could not run.
     *
     * @param station the station's square
     * @param signal the signal's square
     * @return whether it is on the station's own track
     */
    private boolean onItsOwnTrack(TileKey station, TileKey signal)
    {
        if (reducer == null) return false;

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            if (!station.equals(edge.getStart()) && !station.equals(edge.getEnd())) continue;

            for (GraphReducer.TileStep step : edge.getPath()) if (signal.equals(step.getTile())) return true;
        }

        return false;
    }

    /** Whether any of these edges commands the accessory. */
    private static boolean commandedBy(String accessory, java.util.Set<Integer> edges, List<java.util.Set<String>> commands)
    {
        if (accessory == null) return false;

        for (Integer edge : edges) if (commands.get(edge).contains(accessory)) return true;

        return false;
    }

    /** The accessory a signal's square carries, or null - what a route commands is the accessory, by name. */
    private String accessoryOf(TileKey signal)
    {
        org.traincontrol.base.LayoutDiagramComponent component = graph == null ? null : graph.getTiles().get(signal);

        return component == null || component.getAccessory() == null ? null : component.getAccessory().getName();
    }

    /** The list, or an empty one. */
    private static <T> List<T> nonNull(List<T> list)
    {
        return list == null ? java.util.Collections.<T>emptyList() : list;
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
     * The one definition of "broken": starting autonomy is refused when the checks report any ERROR (OB-057), so the
     * diagram strip must decide what to offer from the same count, not from hasBlockingProblems() alone (which asks the
     * GRAPH only, about scissors crossings and unpaired links) - or a setup with four unnamed stations, no blocking
     * problem and four errors, would show a live Start button that every press of is refused.  Adam, OB-090: "the fix it
     * button is not shown, rather just start autonomy when the config had worked before."
     *
     * Blocking problems are counted through check() rather than added to it - AutonomyChecks copies every one of them in
     * as an ERROR finding - with hasBlockingProblems() kept in the disjunction below because check() returns nothing at
     * all when the graph has not been derived yet.
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
     * Here rather than in a panel, because two doors say this and each must pick the singular or plural message by the
     * same count.
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

    // editing

    public void setDirection(TileKey tile, RouteId routeId, Direction direction)
    {
        recordSquare(tile, Collections.singletonMap(routeId, direction));
        directionsTouched();
    }

    /**
     * Records directions wanted on some of one square's roads as its arms can hold them, without re-deriving (OB-351).
     *
     * Every door that sets a direction comes through here, so none can store a road closed a way its square's arrow shows
     * open: on a square whose roads share an arm the arms decide (`TileGraph.armAnswersFor`), and elsewhere each road
     * takes what was wanted.
     *
     * @param tile the square
     * @param wanted road to the direction wanted on it
     */
    private void recordSquare(TileKey tile, Map<RouteId, Direction> wanted)
    {
        Map<RouteId, Route> routes = graph.getRoutes(tile);

        for (Map.Entry<RouteId, Direction> answer : graph.armAnswersFor(tile, wanted).entrySet())
        {
            Route route = routes.get(answer.getKey());

            // the other roads only where their arms changed them
            if (!wanted.containsKey(answer.getKey()) && route != null
                && TileGraph.inForce(graph.getDirection(tile, answer.getKey()), route) == answer.getValue()) continue;

            record(tile, answer.getKey(), answer.getValue());
        }
    }

    /**
     * Records a direction without re-deriving, for callers that are about to set several.
     */
    void record(TileKey tile, RouteId routeId, Direction direction)
    {
        apply(tile, routeId, direction);

        // ONE TRACK, TWO SQUARES (OB-320): a two-square crossing's track runs over both of its squares, so its direction
        // is set on both - in the other square's own terms - whichever square and whichever door it was set from.
        RouteId across = graph == null ? null : graph.crossingRouteOnPartner(tile, routeId);

        if (across != null)
        {
            apply(graph.crossingPartner(tile), across, graph.crossingDirectionOnPartner(tile, routeId, direction));
        }
    }

    /**
     * The directions recorded, derived from - never refused under a standing train (Adam: *"Generally I
     * prefer warnings or errors over outright refusals, since it makes editing easier for the user"*).  What a direction
     * does to a train is the setup check's to say: a train left facing a way its station no longer has is an error
     * (`facingsThatCannotBeHeld`), one left facing track that runs only towards it a warning.  First, each train of the
     * railway's the change leaves no copy facing its way is written into the setup where it stands.
     *
     * @return whether they were set - always
     */
    boolean directionsTouched()
    {
        recordTrainsTheRailwayCannotHold();

        touched();

        return true;
    }

    /**
     * Every train the setup records on a square the railway stands it on no Point of - no copy faces its way - and the way
     * it faces (RSA17-C2): the diagram draws it there, where the error about it says it stands.
     *
     * ALONG ITS TRACK (V348C-C1; Adam: "it must be N/S or a N/S track, or E/W on E/W, or angled on a curved
     * track"): a recorded facing the track does not run - left by an edit of the track under a parked train - is drawn
     * as `facingWhereNoCopySays` draws any train no copy holds, as the caption and the Is Facing menu read it.
     *
     * @return square to facing
     */
    public Map<TileKey, Side> trainsOnNoPoint()
    {
        Map<TileKey, Side> out = new LinkedHashMap<>();

        java.util.Set<String> onTheRailway = railwayTrains().keySet();

        Map<TileKey, String> placed = placedLocomotives();

        for (TileKey square : facingsThatCannotBeHeld())
        {
            String train = placed.get(square);

            if (train == null || onTheRailway.contains(train)) continue;

            Side drawn = facingWhereNoCopySays(square);

            out.put(square, drawn != null ? drawn : getFacing(square));
        }

        return out;
    }

    /**
     * The squares of `trainsOnNoPoint` whose train this configuration pauses (RSA60-C5), for the diagram to draw grey.
     * The setup's list, not the train's flag: the railway does not have these trains, and the setup is what says.
     *
     * @return the squares
     */
    public java.util.Set<TileKey> pausedTrainsOnNoPoint()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        java.util.Set<String> paused = getPausedLocomotives();

        if (paused.isEmpty()) return out;

        Map<TileKey, String> placed = placedLocomotives();

        for (TileKey square : trainsOnNoPoint().keySet())
        {
            if (paused.contains(placed.get(square))) out.add(square);
        }

        return out;
    }

    /**
     * Writes into the setup, where it stands and facing as it faces, each train of the railway's the setup as it now
     * stands gives no copy facing its way (in place of RSA5-A2's and RSA6-A3's refusals).  The copy a
     * train stands on is its direction, so the rebuild cannot stand it on another.  Recorded, the setup says where it is,
     * the check reports it as an error until the track or the train is put right, and the fold keeps it on its square
     * (`capture`).
     */
    private void recordTrainsTheRailwayCannotHold()
    {
        Map<String, Object[]> railway = railwayTrains();

        if (railway.isEmpty()) return;

        AutonomyBuilder after = trialNaming();

        for (Map.Entry<String, Object[]> train : railway.entrySet())
        {
            TileKey square = (TileKey) train.getValue()[0];
            Side facing = (Side) train.getValue()[1];

            if (facing == null || holds(after, square, facing)) continue;

            if (!train.getKey().equals(getLocomotiveNameAt(square))) placeLocomotive(square, train.getKey());

            setFacing(square, facing);

            // AND ITS TAIL (RSA18-A2): the track put back stood it with none, and offered what its tail lies over
            if (train.getValue().length > 2 && train.getValue()[2] != null) setArrivedFrom(square, (String) train.getValue()[2]);

            if (train.getValue().length > 3 && train.getValue()[3] != null)
            {
                setPointProperty(square, "arrivedAlong", new org.json.JSONArray((String) train.getValue()[3]));
            }
        }
    }

    /**
     * Whether a naming has a copy of the square a train facing that way can stand on: one facing it, or a square of one
     * copy recording no facing - one place, as `pointNamedNow` puts a train back.
     */
    private static boolean holds(AutonomyBuilder naming, TileKey square, Side facing)
    {
        Map<String, Side> facings = naming.facingByName();

        int copies = 0;
        boolean recordsNone = false;

        for (Map.Entry<String, TileKey> copy : naming.tilesByName().entrySet())
        {
            if (!square.equals(copy.getValue())) continue;

            copies++;

            Side way = facings.get(copy.getKey());

            if (way == null) recordsNone = true;

            if (way == facing) return true;
        }

        return copies == 1 && recordsNone;
    }

    /**
     * The railway's trains, each to its square and the way it faces, or null: by the square and facing each running Point
     * says (RSA6-A2), else by its name through today's naming.
     */
    private Map<String, Object[]> railwayTrains()
    {
        Map<String, Object[]> standing = new LinkedHashMap<>();

        org.traincontrol.automation.Layout running = runningLayout == null ? null : runningLayout.get();

        if (running == null) return standing;

        for (org.traincontrol.automation.Point point : running.getPoints())
        {
            Locomotive loc = point.getCurrentLocomotive();

            // BY THE SQUARE AND FACING THE RUNNING POINT SAYS (RSA6-A2), else by its name through today's naming
            TileKey square = point.getSquare() == null ? null : AutonomyCompanionStore.parseTileKey(point.getSquare());
            Side facing = sideNamed(point.getCopyFacing());

            if (square == null) square = squareOfNameNow.get(point.getName());
            if (facing == null) facing = facingOfNameNow.get(point.getName());

            if (loc == null || loc.getName() == null || square == null) continue;

            // AND THE SIDE IT CAME IN BY AND ITS ROAD, for a record of it (RSA18-A2)
            standing.putIfAbsent(loc.getName(), new Object[] {square, facing, point.getArrivedFrom(),
                org.traincontrol.automation.Layout.namesOfRoad(point.getArrivedAlong())});
        }

        return standing;
    }

    /**
     * The trains standing, each to its square and the way it is said to face, or null: the railway's (`railwayTrains`),
     * and the setup's where the railway has none of them.
     */
    private Map<String, Object[]> standingTrains()
    {
        Map<String, Object[]> standing = railwayTrains();

        String active = store.getActiveConfiguration();

        org.json.JSONObject configuration = active == null ? null : store.getConfiguration(active);

        org.json.JSONObject points = configuration == null ? null : configuration.optJSONObject("points");

        for (String id : points == null ? Collections.<String>emptySet() : points.keySet())
        {
            TileKey square = AutonomyCompanionStore.parseTileKey(id);

            String train = square == null ? null : getLocomotiveNameAt(square);

            if (train != null) standing.putIfAbsent(train, new Object[] {square, getFacing(square)});
        }

        return standing;
    }

    /**
     * The squares a standing train faces track from that runs only towards it - near the train only: no edge of the
     * railway leaves the square by the side it faces, and one arrives by it (a warning, judged near the
     * train).  Track closed there is not this: nothing arrives by the side either.
     *
     * @return the squares
     */
    private Set<TileKey> trainsFacingTrackTowardsThem()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null) return out;

        for (Object[] train : standingTrains().values())
        {
            TileKey square = (TileKey) train[0];
            Side facing = (Side) train[1];

            if (square == null || facing == null) continue;

            if (!leavesBy(reducer, square, facing) && arrivesBy(reducer, square, facing)) out.add(square);
        }

        return out;
    }

    /**
     * The stations at the end of a line that trains may not turn at: the railway reaches the square by one side only, and
     * neither may nor must a train change direction there - so a train sent there could never leave (an error).
     *
     * @return the squares
     */
    private Set<TileKey> stationsAtTheEndOfALine()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (!store.isStation(tile) || Boolean.FALSE.equals(getPointProperty(tile, "active")) || isTurnAround(tile))
            {
                continue;
            }

            Set<Side> sides = java.util.EnumSet.noneOf(Side.class);

            for (GraphReducer.ReducedEdge edge : reducer.getEdges())
            {
                if (tile.equals(edge.getStart()) && edge.getExitSide() != null) sides.add(edge.getExitSide());
                if (tile.equals(edge.getEnd()) && edge.getEntrySide() != null) sides.add(edge.getEntrySide());
            }

            if (sides.size() == 1) out.add(tile);
        }

        return out;
    }

    /** Whether an edge of this reduction leaves the square by this side. */
    private static boolean leavesBy(GraphReducer reduction, TileKey square, Side side)
    {
        for (GraphReducer.ReducedEdge edge : reduction.getEdges())
        {
            if (square.equals(edge.getStart()) && edge.getExitSide() == side) return true;
        }

        return false;
    }

    /** Whether an edge of this reduction arrives at the square by this side. */
    private static boolean arrivesBy(GraphReducer reduction, TileKey square, Side side)
    {
        for (GraphReducer.ReducedEdge edge : reduction.getEdges())
        {
            if (square.equals(edge.getEnd()) && edge.getEntrySide() == side) return true;
        }

        return false;
    }

    /**
     * The naming of a reduction of the graph as the setup now stands, made as a rebuild makes it and not installed
     * (RSA5-A2) - a direction recorded, or a link switched off (RSA6-A3), which the graph held may not reflect.
     */
    private AutonomyBuilder trialNaming()
    {
        TileGraph trialGraph = new TileGraph(pages, store.getExcludedPages());

        store.applyTo(trialGraph);

        trialGraph.validatePortals();

        GraphReducer trial = new GraphReducer(trialGraph, store.asAuthored());

        trial.reduce();

        return builder(trial, null);
    }

    private void apply(TileKey tile, RouteId routeId, Direction direction)
    {
        graph.setDirection(tile, routeId, direction);

        // stored only when it differs from what the graph would default to, so a default never looks
        // like a decision somebody made
        store.setTileDirection(tile, routeId,
            direction == graph.defaultDirection(tile, routeId) ? null : direction);
    }

    /**
     * Every route whose setting shuts a move the hardware allows - one-way and closed track, and the switches still at their
     * base-to-forks default - for Allow Every Path (FR-108).  A turnout with no address can only be trailed through
     * whatever is set; it is among them only if it has been closed as well, and opening it leaves it the one way the
     * hardware runs it.
     *
     * @return each such square, to its routes
     */
    public Map<TileKey, List<RouteId>> routesNotOpenBothWays()
    {
        Map<TileKey, List<RouteId>> out = new LinkedHashMap<>();

        if (graph == null) return out;

        for (TileKey tile : graph.getTiles().keySet())
        {
            for (Map.Entry<RouteId, Route> route : graph.getRoutes(tile).entrySet())
            {
                // a stub has no move through it to open
                if (route.getValue().getA() == route.getValue().getB()) continue;

                if (graph.getDirection(tile, route.getKey()) == Direction.BOTH) continue;

                if (shutsAMoveTheHardwareAllows(tile, route.getKey(), route.getValue()))
                {
                    out.computeIfAbsent(tile, k -> new ArrayList<RouteId>()).add(route.getKey());
                }
            }
        }

        return out;
    }

    /** Whether a route's setting refuses a train a move the hardware would let it make, as `TileGraph.exits` answers. */
    private boolean shutsAMoveTheHardwareAllows(TileKey tile, RouteId routeId, Route route)
    {
        for (Side entry : new Side[] {route.getA(), route.getB()})
        {
            if (!route.isTraversableFrom(entry)) continue;

            boolean allowed = false;

            for (TileGraph.Exit exit : graph.exits(tile, entry))
            {
                if (routeId.equals(exit.getRouteId())) allowed = true;
            }

            if (!allowed) return true;
        }

        return false;
    }

    /**
     * Opens both ways every route that can be (FR-108; Adam: *"add a bulk tool to allow all paths, i.e. remove
     * any red arrows in one go. With warning."*), recorded at once and re-derived once, as the bulk setter below does.
     *
     * @return how many squares were opened
     */
    public int allowEveryPath()
    {
        Map<TileKey, List<RouteId>> shut = routesNotOpenBothWays();

        if (shut.isEmpty()) return 0;

        for (Map.Entry<TileKey, List<RouteId>> square : shut.entrySet())
        {
            Map<RouteId, Direction> open = new LinkedHashMap<>();

            for (RouteId routeId : square.getValue()) open.put(routeId, Direction.BOTH);

            recordSquare(square.getKey(), open);
        }

        directionsTouched();

        return shut.size();
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
            Map<RouteId, Direction> every = new LinkedHashMap<>();

            for (RouteId routeId : graph.getRoutes(tile).keySet()) every.put(routeId, direction);

            recordSquare(tile, every);
        }

        directionsTouched();
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
        recordSquare(tile, directions);

        directionsTouched();
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

        // NOT REFUSED UNDER A STANDING TRAIN (in place of RSA6-A3's refusal): a train that came in
        // through the link is written into the setup where it stands, and the check says so as an error
        recordTrainsTheRailwayCannotHold();

        touched();
    }

    /**
     * Renames a station.
     *
     * Nothing else has to happen.  A caption points at the station's SQUARE, so it follows a rename without being touched.
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
     * Whether another square already has this name, in any case (FR-120) - asked as a sensor is named on becoming a station.
     *
     * @param name the name as typed
     * @param except the square being named, whose own name does not count
     * @return true where another square is called that
     */
    public boolean isPointNameTaken(String name, TileKey except)
    {
        if (name == null || name.trim().isEmpty()) return false;

        String wanted = name.trim();

        for (TileKey tile : store.getNamedTiles())
        {
            if (tile.equals(except)) continue;

            String other = store.getPointName(tile);

            if (other != null && other.trim().equalsIgnoreCase(wanted)) return true;
        }

        return false;
    }

    /**
     * Why a typed name may not be given to a square, or null when it may (TDR-C11).
     *
     * A name ending in the builder's own direction heading - "Main (eastbound)" - reads as another square
     * wherever a message names squares, because that heading is exactly what is stripped from a copy's name.
     * Adam: *"refuse and close, but make sure the words are uncommon"* - so only the builder's
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
        // station again (Adam, OB-291: *"make max train length be remembered if a station is changed to a
        // non-station, and then restored if it is changed back to a station.  don't modify the behavior of this
        // attribute: it is still to be ignored for non-stations."*).  Ignored where it is kept: the running layout reads it
        // only of a destination (`Point.validateTrainLength`), the station menu is the only door that shows it, and every
        // reader here asks about stations - `tilesWithAMaxTrainLength` and `modelsAnyLength` included.
        store.setStation(tile, station);

        // AND SO DOES EVERY OTHER STATION SETTING (GSE-C4): its caption, the sides it bars arrivals by, its exit guard and
        // its entry guards - and the editor keeps the old "terminus" turn flag beside them, ignored while not a station.
        // Each is ignored where the setting is READ, since a caption left drawn would light a station name up on a plain
        // point, and a guard would be thrown red over a square nothing is sent to.  So the session's views of them
        // (`getCaptionTarget`, `getLabelledStationTiles`, `barredArrivals`,
        // `getBarredArrivals`, the guard getters, and the accessory names the build is given) leave out a square that is
        // not a station now, and the checks are handed those views.  What the store keeps comes back when the square is
        // promoted, which is the operator's own act.

        // NOT being unavailable while another square is occupied (Adam, reversing AMS-B2: "do allow
        // restrictions on non-stations, and let's not clear them when the type changes").  It stays in force on a square
        // that is not a station - the build locks every route into it against the watched square - and it is taken off
        // where it was put on, under Advanced Parameters, which every sensor square has.  The findings list names it
        // whatever the square is (UNAVAILABLE_WHILE_OCCUPIED).

        touched();
    }

    /**
     * The name of the locomotive this configuration puts on a square.
     *
     * A placement is an OBJECT - {"name": ..., "speed": ..., "arrivalFunc": ...} - because parseAuto resets whatever a
     * placement omits, so a train's length and functions travel with its name.  Anything wanting only the name has to
     * unwrap it: String.valueOf on a JSONObject gives its JSON.  One place knows the shape (`nameOfPlacedLocomotive`).
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

            // The facing described the train that was standing there, not the square: left behind, the next locomotive
            // placed here would inherit the last one's direction without being asked.
            setPointProperty(tile, AutonomyBuilder.FACING, null);

            // AND SO DID THE ARRIVAL SIDE (IND9-A1), for the same reason and more strongly: where a train's tail lies is a
            // fact about that train and nothing else.  Left behind, parseAuto reads it back onto whatever is placed here
            // next, and the walk that blocks track behind a standing train follows it.
            setPointProperty(tile, "arrivedFrom", null);

            // AND THE ROAD, which describes the same arrival (WK7-B1).
            setPointProperty(tile, "arrivedAlong", null);

            return;
        }

        Object existing = getPointProperty(tile, "loc");

        org.json.JSONObject loc = existing instanceof org.json.JSONObject
            ? new org.json.JSONObject(existing.toString()) : new org.json.JSONObject();

        loc.put("name", name);

        // And wherever else it was, it is not there now.  Moving a train in the running layout takes it off the point it was
        // on, but the configuration is never told, and it is what the next build reads: the build would emit the
        // locomotive at two Points, and fromJSON answers that by invalidating the WHOLE layout, so every path is refused as
        // "configuration is invalid" with nothing pointing back at the placement that caused it.
        forgetPlacementsElsewhere(tile, name, loc);

        setPointProperty(tile, "loc", loc);

        // AND A NEW OCCUPANT DOES NOT INHERIT THE OLD ONE'S TAIL (IND9-A1), as `Point.setLocomotive` does on the running
        // layout: a square whose occupant changed through a door that does not ask (the editor's Place/Edit Locomotive
        // item) would keep the previous train's arrival side.  Cleared rather than guessed: the doors that CAN work the
        // side out write it immediately after calling this.
        if (!name.equals(nameOfPlacedLocomotive(existing)))
        {
            setPointProperty(tile, "arrivedFrom", null);
            setPointProperty(tile, "arrivedAlong", null);
        }
    }

    /**
     * Takes every locomotive off the setup, re-deriving the station index once (REL-C4).
     *
     * The twin of `clearEveryHome`: clearing one placement writes TWO properties (the placement and the facing that
     * described the train), so the loop that did it square by square cost 2N full builder constructions on the event
     * thread.  Through the same writer the single-square door uses, so the two cannot come to disagree about what clearing
     * a placement means; only the re-derive differs.
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
     * One thing a configuration held of a locomotive the database does not have, and let go of (OB-349).
     */
    public static final class LocomotiveLeftOut
    {
        /** What it held: the train standing on the square, its home there, or its name on the square's list. */
        public enum What { STANDING, HOME, EXCLUDED, ALLOWED }

        /** The locomotive's name. */
        public final String locomotive;

        /** What was let go of. */
        public final What what;

        /** The square it was on. */
        public final TileKey square;

        /**
         * @param locomotive the locomotive's name
         * @param what what was let go of
         * @param square the square it was on
         */
        public LocomotiveLeftOut(String locomotive, What what, TileKey square)
        {
            this.locomotive = locomotive;
            this.what = what;
            this.square = square;
        }

        @Override
        public String toString()
        {
            return locomotive + " " + what + " " + square;
        }
    }

    /**
     * Takes every locomotive the database does not hold out of a configuration (OB-349): a train standing on a square,
     * with the facing and the arrival recorded for it; a home, with the way it was set facing; and a name on a square's
     * list.  Everything else about each square is kept as it is.
     *
     * The loader drops each of these from the railway it builds, and the setup kept them, so every reader of the setup
     * showed a train the railway did not have and every save wrote it back; taken out here, the two agree.  A run list is
     * not held apart from the placements, so a train taken off its square is off it too.  The timetable and the paused
     * trains are left to the loader, which drops a name it cannot find from each.
     *
     * @param configuration the configuration's name
     * @param known the names the locomotive database holds
     * @return what was let go of, by locomotive and then square; empty when the configuration names none it lacks
     */
    public java.util.List<LocomotiveLeftOut> forgetLocomotivesNotIn(String configuration,
        java.util.Collection<String> known)
    {
        java.util.List<LocomotiveLeftOut> out = new java.util.ArrayList<>();

        // NOTHING AGAINST AN EMPTY DATABASE: a missing or unread one would otherwise take every train out of the setup and
        // save it.  The build still leaves such trains off the railway.
        if (known == null || known.isEmpty()) return out;

        org.json.JSONObject held = configuration == null ? null : store.getConfiguration(configuration);

        org.json.JSONObject points = held == null ? null : held.optJSONObject("points");

        if (points == null) return out;

        boolean changed = false;

        for (String key : points.keySet())
        {
            org.json.JSONObject point = points.optJSONObject(key);

            TileKey square = AutonomyCompanionStore.parseTileKey(key);

            if (point == null || square == null) continue;

            // THE TRAIN STANDING HERE, with the keys `placeLocomotive` clears when a train is taken off: the two must agree
            String standing = nameOfPlacedLocomotive(point.opt(AutonomyBuilder.LOCOMOTIVE));

            if (standing != null && !known.contains(standing))
            {
                point.remove(AutonomyBuilder.LOCOMOTIVE);
                point.remove(AutonomyBuilder.FACING);
                point.remove("arrivedFrom");
                point.remove("arrivedAlong");

                out.add(new LocomotiveLeftOut(standing, LocomotiveLeftOut.What.STANDING, square));
            }

            // THE HOME, with the keys `clearEveryHome` clears
            String home = point.optString("home", "").trim();

            if (!home.isEmpty() && !known.contains(home))
            {
                point.remove("home");
                point.remove(AutonomyBuilder.HOME_FACING);

                out.add(new LocomotiveLeftOut(home, LocomotiveLeftOut.What.HOME, square));
            }

            // AND THE SQUARE'S LIST, whichever way it reads (FR-121).  Emptied, it goes as the editor writes an empty one,
            // and the mode stays: the railway built reads the same list the same way.
            org.json.JSONArray listed = point.optJSONArray("excludedLocs");

            if (listed != null)
            {
                boolean allowOnly = org.traincontrol.automation.Point.ALLOW_ONLY_LISTED.equals(
                    point.optString(org.traincontrol.automation.Point.EXCLUDED_LOCS_MODE, null));

                org.json.JSONArray kept = new org.json.JSONArray();

                for (int at = 0; at < listed.length(); at++)
                {
                    Object name = listed.opt(at);

                    if (!(name instanceof String) || known.contains(name))
                    {
                        kept.put(name);
                    }
                    else if (!((String) name).trim().isEmpty())
                    {
                        out.add(new LocomotiveLeftOut((String) name,
                            allowOnly ? LocomotiveLeftOut.What.ALLOWED : LocomotiveLeftOut.What.EXCLUDED, square));
                    }
                }

                if (kept.length() != listed.length())
                {
                    if (kept.length() == 0) point.remove("excludedLocs");
                    else point.put("excludedLocs", kept);

                    changed = true;
                }
            }
        }

        if (out.isEmpty() && !changed) return out;

        dirty = true;

        // The split names are computed from these properties, as `setPointProperty` says
        if (configuration.equals(store.getActiveConfiguration())) deriveStationIndex();

        out.sort(java.util.Comparator.<LocomotiveLeftOut, String>comparing(left -> left.locomotive)
            .thenComparing(left -> left.what).thenComparing(left -> left.square.toString()));

        return out;
    }

    /**
     * The locomotive named by a stored placement, or null when there is none.
     *
     * THE ONE PLACE THAT KNOWS THE SHAPE.  `getLocomotiveNameAt` is the same question asked of a square rather than of a
     * value.  A placement is an OBJECT - {"name": ..., "speed": ..., "arrivalFunc": ...} - because parseAuto resets
     * whatever a placement omits.  A BARE STRING is not a shape this program writes, but setup files get edited by hand
     * and an older autonomy.json may hold one (RGD-C5): this method is also what the occupant-change guards ask, so
     * answering null for a bare string would make re-placing a train on its own square read as a CHANGE of occupant and
     * throw away an arrival side that was still true.
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
     * One per copy the build emits for the square, which is what a train standing there can actually be: the ONWARD side
     * of the route it came in on, not the opposite of the side it entered by.  Those two agree on a straight and differ on
     * a curve (MT-125, NR-7) - a train entering an N-E curve by N leaves by E, and saying it faces S describes a train
     * sitting across the rails.
     *
     * The sides come from arrivalSides - the same door the build splits on - not from a second walk of the reduced edges
     * (DR-B6).  A square the door declines to split offers no facing at all, which is right: the build emits it whole.
     * Ordered the way the builder orders its copies.  The first answer is NOT what a placement with no recorded facing
     * gets: that is `startableCopy`'s choice, the first copy trains may arrive at (GUI2-B1, TDY4-C1).
     *
     * @param tile
     * @return the possible facings, empty when nothing reaches the square and a single entry - which
     *         needs no asking about - when only one line does
     */
    public List<Side> facingChoices(TileKey tile)
    {
        // ASKED OF THE BUILD (DR-B6): `facingsFor` reads `StationIndex`, which reads `builder.facingByName()`, so this is the
        // build's own list of what its copies face - which is the question the menu is asking.  A facing has to be one the
        // build can HOLD, or `placementCopy` falls back to `startableCopy` and quietly turns the train round.  That gives:
        //
        // - the ONWARD side rather than the opposite of the arrival, as `facingOf` gives a plain copy (MT-125);
        // - the arrival side on a turn-around square, because the build emits a turning copy and gives it the arrival side
        //   (OB-145);
        // - the plain copy first, because `nodesFor` emits it first and `placementCopy` prefers it - a train standing where
        //   it MAY turn round has not turned round yet;
        // - nothing at all on a square the build emits whole, which has no facing to offer.
        Set<Side> out = new java.util.LinkedHashSet<>(facingsFor(tile).values());

        return new ArrayList<>(out);
    }


    /**
     * The diagram tiles a standing train is lying across, for greying out.
     *
     * Adam: **"Locked tiles in this way should be greyed out until the train blocking it moves."**
     *
     * **The rule is asked of the railway, not restated here.**  `Layout` decides which edges a train covers, and this only
     * translates that answer into the squares the diagram draws; computing it a second time from the reducer would be two
     * statements of one question that drift.  The join is by SQUARE: a runtime `Edge` runs between two `Point`s, a `Point`
     * maps back to the square it was built from, and the reducer holds the tiles that lie between two squares.  Both
     * directions of a reduced edge are matched, because the tail is lying across that rail either way.
     *
     * The endpoint squares are NOT included: a train standing at a sensor already shows as standing there.  Adam's ruling
     * is about the track between: *"edges, because the points are technically unoccupied"*.
     *
     * **AS FAR BACK AS THE TRAIN REACHES, AND NO FURTHER (MT-309).**  The railway's answer is per EDGE (a train lying across
     * any part of a segment makes the whole segment impassable), but an edge can be a dozen squares long, so the squares are
     * walked one at a time from where the train stands, each paying its own length, and the walk stops when the train has
     * been used up.  What comes back is a SUBSET of what the railway holds covered, which is the safe direction: a picture
     * claiming more blocked track than the railway holds is the failure `Layout.edgesCoveredByStandingTrains` was widened
     * to avoid.  Nothing here changes what the railway blocks.
     *
     * That is the indicator saying WHERE THE TRAIN IS (Adam: *"we need to draw a line ... to show that the train is
     * That is the indicator saying WHERE THE TRAIN IS (Adam: *"we need to draw a line ... to show that the train is
     * there."*), not what is unavailable, which has its own answer, `tilesBlockedByStandingTrains` below, greyed while
     * autonomy is running.
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
     * Adam: *"instead of shading the entire tiles, we need to draw a line (let's say in orange) to show that
     * the train is there.  graying makes it look confusing on double curve tiles."*  A double curve carries two roads that
     * never meet, and a mark over the whole square says a train is on both of them.
     *
     * The road is not worked out here: `GraphReducer.TileStep` records the route each step of an edge runs through, so the
     * walk keeps what it already had in its hand.
     *
     * @param running the layout, which is what knows where the trains are
     * @return each covered square, with the routes of it the train is lying across
     */
    public Map<TileKey, Set<RouteId>> routesCoveredByStandingTrains(
        org.traincontrol.automation.Layout running)
    {
        Map<TileKey, Set<RouteId>> out = new LinkedHashMap<>();

        if (running == null || reducer == null || getStationIndex() == null) return out;

        // Grouped by TRAIN, because the walk below is a walk backwards from one train and the railway's map has thrown that
        // away - it answers the routing question, "is this edge covered".
        //
        // AND KEPT AS THE EDGES THEMSELVES (OB-239), each the reduction's own edge with its places (`reducedEdgeOf`): with
        // only the two squares at each end, the line would go down whichever of two roads joining one pair of sensors the
        // reduction listed first.  Painting each covered edge's own places, the line and the railway cannot name different
        // roads.
        Map<org.traincontrol.base.Locomotive, List<GraphReducer.ReducedEdge>> reach = new LinkedHashMap<>();

        for (Map.Entry<org.traincontrol.automation.Edge, org.traincontrol.base.Locomotive> covered
            : running.edgesCoveredByStandingTrains().entrySet())
        {
            org.traincontrol.automation.Edge edge = covered.getKey();

            if (edge == null || edge.getStart() == null || edge.getEnd() == null) continue;

            GraphReducer.ReducedEdge rail = reducedEdgeOf(edge);

            if (rail == null) continue;

            List<GraphReducer.ReducedEdge> rails = reach.get(covered.getValue());

            if (rails == null)
            {
                rails = new ArrayList<>();

                reach.put(covered.getValue(), rails);
            }

            // ONCE EACH: several of the railway's copies of a rail are one edge of the reduction
            if (!rails.contains(rail)) rails.add(rail);
        }

        for (Map.Entry<org.traincontrol.base.Locomotive, List<GraphReducer.ReducedEdge>> train : reach.entrySet())
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
     * switch where they part (MT-477) - as places, which the grey is drawn from, and not as an edge.  A train with no road
     * is every train stood by hand, answered Not known, or re-stood by a restart or an edit (Adam, MT-543: *"With not
     * known, there is no orange tail."*).
     *
     * Each claimed square is drawn along the road every rail the train can lie along agrees on.  Where they disagree - the
     * switch itself, whose legs are different roads - the square is listed and no line drawn: the train is on it, and on
     * which leg is not known.  The grey still says it is taken.
     *
     * **THE RAILS INTO ITS SQUARE THAT RUN OVER ALL ITS CLAIMS, not every edge through a claimed square (OB-348).**  The
     * railway claims what the rails arriving at the platform share (`Layout.claimUpToWhereTheRailsPart`), so those rails
     * say which road the tail is on; a switch behind the platform whose other leg carries rails that never reach it must
     * not read as two roads (Adam: *"it is not drawn over the thrown arm of the switch and 7,8, even though I
     * see orange before and after."*).  Where no such rail is found, every edge is asked.
     *
     * @param running the layout
     * @param drawn the trains the walk above drew
     * @param out the squares to draw, added to
     */
    private void drawTheTrainsThatCoverNoEdge(org.traincontrol.automation.Layout running,
        Set<org.traincontrol.base.Locomotive> drawn, Map<TileKey, Set<RouteId>> out)
    {
        // PER TRAIN, so each is asked of its own rails (OB-348)
        Map<org.traincontrol.base.Locomotive, Set<String>> claimedBy = new LinkedHashMap<>();

        for (Map.Entry<String, org.traincontrol.base.Locomotive> claim
            : running.placesCoveredByStandingTrains().entrySet())
        {
            if (drawn.contains(claim.getValue())) continue;

            Set<String> claims = claimedBy.get(claim.getValue());

            if (claims == null)
            {
                claims = new java.util.HashSet<>();

                claimedBy.put(claim.getValue(), claims);
            }

            claims.add(claim.getKey());
        }

        for (Map.Entry<org.traincontrol.base.Locomotive, Set<String>> train : claimedBy.entrySet())
        {
            Set<String> claimed = train.getValue();

            // Every road each claimed square is run along, by every rail naming the claim: a step by the route it records,
            // and the square a rail arrives at - a Point's, which is never a step - by the side the rail reaches it.
            Map<TileKey, Set<RouteId>> seen = new LinkedHashMap<>();

            // And how far back from the platform each lies, in squares along the rail (V348B-C1)
            Map<TileKey, Integer> back = new LinkedHashMap<>();

            List<GraphReducer.ReducedEdge> rails = railsATailLiesAlong(running, train.getKey(), claimed);

            // How far back is measured from the platform only on rails into it; on the every-edge fallback it is not
            // measured at all, and nothing is cut (V348C-D1)
            boolean fromThePlatform = rails != null;

            for (GraphReducer.ReducedEdge edge : fromThePlatform ? rails : reducer.getEdges())
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

                    RouteId road = onTheWay ? path.get(at).getRouteId() : roadOn(edge.getEnd(), edge.getEntrySide());

                    Set<RouteId> roads = seen.get(tile);

                    if (roads == null)
                    {
                        roads = new LinkedHashSet<>();

                        seen.put(tile, roads);
                    }

                    if (road != null) roads.add(road);

                    int behind = places.size() - 1 - at;

                    Integer was = back.get(tile);

                    if (was == null || behind < was) back.put(tile, behind);
                }
            }

            // NOTHING PAST THE FIRST SQUARE WHOSE ROAD IS NOT KNOWN (V348B-C1; Adam: "stop at unknown.  but if
            // it's not thrown and the train must pass through the thrown arm, then do draw it").  Beyond a switch the tail
            // may have come through on either leg, the train lies on one road or the other, and a line along the one the
            // railway drives would say which.  A switch every rail into the platform crosses on the same arm is not
            // unknown, and is drawn along that arm whichever way it is set: nothing here reads a switch's position.
            int stop = Integer.MAX_VALUE;

            for (Map.Entry<TileKey, Set<RouteId>> square : seen.entrySet())
            {
                if (fromThePlatform && square.getValue().size() > 1) stop = Math.min(stop, back.get(square.getKey()));
            }

            for (Map.Entry<TileKey, Set<RouteId>> square : seen.entrySet())
            {
                if (back.get(square.getKey()) > stop) continue;

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
    }

    /**
     * The rails a train with no covered edge can be lying along (OB-348): every rail of the reduction into the square it
     * stands on by the side it came in by, where one of them runs over every place it claims - or, where its side is not
     * known, the rails into the square that run over every place it claims - or, where there are none, null, for every edge
     * of the reduction, as `drawTheTrainsThatCoverNoEdge` asked before.
     *
     * EVERY RAIL BY ITS SIDE, NOT ONLY THE ONES AUTONOMY DRIVES.  The railway claims what the rails it was built with share,
     * and a rail the build leaves out - against a one-way arrow - is not among them; but a train placed by hand may have
     * come along it.  Where such a rail runs through a switch behind the platform on the other leg, which leg the tail is
     * on is not known, and the switch is drawn along no road, as it was before.
     *
     * @param running the layout
     * @param train the train
     * @param claimed the places it claims
     * @return the reduced edges to ask which road each claimed square is on, or null for every edge
     */
    private List<GraphReducer.ReducedEdge> railsATailLiesAlong(org.traincontrol.automation.Layout running,
        org.traincontrol.base.Locomotive train, Set<String> claimed)
    {
        org.traincontrol.automation.Point standing = running.whereTheTrainIs(train);

        TileKey at = standing == null ? null : getStationIndex().squareOf(standing.getName());

        String side = standing == null ? null : standing.getArrivedFrom();

        List<GraphReducer.ReducedEdge> bySide = new ArrayList<>();
        List<GraphReducer.ReducedEdge> overAll = new ArrayList<>();

        boolean sideRunsOverAll = false;

        for (GraphReducer.ReducedEdge edge : at == null ? java.util.Collections.<GraphReducer.ReducedEdge>emptyList()
            : reducer.getEdges())
        {
            if (!at.equals(edge.getEnd())) continue;

            Set<String> along = new java.util.HashSet<>();

            for (GraphReducer.Place place : reducer.placesAlong(edge)) along.add(place.getId());

            boolean all = along.containsAll(claimed);

            if (all) overAll.add(edge);

            if (side != null && edge.getEntrySide() != null && side.equalsIgnoreCase(edge.getEntrySide().name()))
            {
                bySide.add(edge);

                sideRunsOverAll |= all;
            }
        }

        if (sideRunsOverAll) return bySide;

        return overAll.isEmpty() ? null : overAll;
    }

    /**
     * The diagram tiles standing trains have BLOCKED, for greying out.
     *
     * Adam: *"'train is here' should also mean 'track is blocked' - that is the whole point.  it's the same as greying out
     * edges, just in a different way."*  And, choosing how: *"can we just grey out the tiles just like blocked edges while
     * autonomy is running?  That plus the line, drawn and refreshed carefully, should do the trick."*
     *
     * **What routing actually refuses: the squares standing trains claim** (OB-280).  `routesCoveredByStandingTrains`
     * answers "where is the train"; this one answers "what may not be used" - the places the runtime's own tail walk
     * claims, which is what `isPathClear` refuses a path over.  Not restated: `Layout.placesCoveredByStandingTrains`
     * decides which places a train claims, and this only translates them into squares.
     *
     * WHILE AUTONOMY IS RUNNING is not decided here: this says what is blocked, and `TrainControlUI.refreshCoveredTrack`
     * asks whether that is worth drawing.
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
     * **What routing actually refuses: the squares standing trains claim.**  Adam: *"grey what routing
     * actually refuses.  if the train doesn't protrude past the switch, there should be nothing else to gray."*  So the
     * grey is `Layout.placesCoveredByStandingTrains` - the places the runtime's own tail walk claims, which is what
     * `isPathClear`'s shared-metal sweep refuses a path over (OB-207) - translated into squares.  A train that fits on its
     * berth claims that square alone (OB-278), and nothing past the switch behind it is grey.  (The whole of every covered
     * EDGE would be wrong: a path crossing only the far end of the edge is not refused.)
     *
     * **Asked of the railway, not restated.**  The reduction only says which square, and which road of it, each place id
     * names - the ids `GraphReducer.placesAlong` built, one per step and one for the square the edge arrives at.
     *
     * **Per road where the place is per road**: a double curve or an overpass carries two roads that never meet, and its
     * place ids name the road, so the tile fades that road and leaves the other at full strength - the band
     * `LayoutLabel.theRoadToFade` draws.  Everywhere else the place is the whole square, and it is returned with no road
     * named, which fades the whole tile: a train on one leg of a turnout blocks the other.
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
     * and stops the moment the train has been used up.  Only the edges the RAILWAY holds covered are
     * walked, each along its own squares in order (OB-239), so this can only ever draw a subset of them.
     *
     * A square with no length assigned costs nothing, which is the same convention every other length
     * rule here uses - `getTileLength` answers 0 for unmeasured, and "only positive lengths are
     * determinate".  An unmeasured square is therefore drawn and the walk carries on; it cannot run
     * away, because it can only go as far as the covered edges reach.
     *
     * @param running the layout
     * @param train the locomotive
     * @param covered every edge this train covers, as the reduction's own edges (`reducedEdgeOf`)
     * @param out the squares to draw, added to
     */
    private void walkBackFrom(org.traincontrol.automation.Layout running,
        org.traincontrol.base.Locomotive train, List<GraphReducer.ReducedEdge> covered,
        Map<TileKey, Set<RouteId>> out)
    {
        if (train == null || train.getTrainLength() == null) return;

        int remaining = train.getTrainLength();

        if (remaining <= 0) return;

        // WHERE THE TRAIN IS, not which Point holds it (VD12-B1): a locked path reserves every Point on it and
        // `getLocomotiveLocation` returns the first in iteration order.  `whereTheTrainIs` is the rule `DiagramMonitor`'s
        // marker follows.
        org.traincontrol.automation.Point standing = running.whereTheTrainIs(train);

        if (standing == null) return;

        TileKey at = getStationIndex().squareOf(standing.getName());

        if (at == null) return;

        Set<TileKey> walked = new LinkedHashSet<>();

        walked.add(at);

        // THE STANDING SQUARE IS SPENT FIRST, as the guard spends it (Adam, OB-278: *"the 2 length tile with the
        // s88 consumes 2 units of the train"*, and *"Everywhere"*).  A train no longer than the square it stands on is drawn
        // on that square and nowhere behind it.  The orange says where the train IS (OB-277); what it blocks is the grey.
        while (remaining > 0)
        {
            // THE NEXT COVERED EDGE ALONG, walked along its own squares (OB-239) - see `nextCoveredEdge`.  The walk
            // cannot double back: `walked` holds every square already passed, which is the same guard `Layout`'s own
            // tail walk uses against a loop of track.
            GraphReducer.ReducedEdge rail = nextCoveredEdge(covered, at, walked);

            if (rail == null) return;

            TileKey next = at.equals(rail.getEnd()) ? rail.getStart() : rail.getEnd();

            // THE SENSOR SQUARES ARE DRAWN TOO (Adam, OB-277: *"when we draw orange lines, they don't overlap with
            // sensors"*, and on every sensor, occupied or not): the square a train stands on is a sensor, and so is every
            // Point its tail lies back across, so leaving them out broke the line at exactly the squares that say where
            // the train is.  The standing square is drawn once, when the first hop says which of its sides the body leaves
            // by, and then its length is spent, before any square behind it (OB-278).
            if (walked.size() == 1)
            {
                markTheSensor(out, at, rail);

                remaining -= store.getTileLength(at);

                if (remaining <= 0) return;
            }

            List<GraphReducer.TileStep> between = stepsFrom(rail, at);

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
            markTheSensor(out, next, rail);

            remaining -= store.getTileLength(next);

            walked.add(next);

            at = next;
        }
    }

    /**
     * The reduction's own edge for an edge the railway holds covered, or null where the reduction has none with its
     * places (OB-239).
     *
     * The edge from the same square to the same square whose places (`GraphReducer.placesAlong`) are the ones the
     * railway's edge carries, in the same order - which is what the build wrote them from.  Where the railway's edge
     * carries none - a hand-written configuration, or one written before 3.0.0 - the two squares and the direction
     * decide it, and the reduction keeps one edge for those.  An edge of a railway built before the diagram was redrawn
     * can find none, and is not drawn rather than drawn along a road the railway does not hold; a train none of whose
     * edges is found is drawn from the places it claims (`drawTheTrainsThatCoverNoEdge`).
     *
     * @param covered the railway's edge
     * @return the reduced edge, or null
     */
    private GraphReducer.ReducedEdge reducedEdgeOf(org.traincontrol.automation.Edge covered)
    {
        TileKey from = getStationIndex().squareOf(covered.getStart().getName());
        TileKey to = getStationIndex().squareOf(covered.getEnd().getName());

        if (from == null || to == null || reducer == null) return null;

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            if (!from.equals(edge.getStart()) || !to.equals(edge.getEnd())) continue;

            if (covered.getPlaceIds().isEmpty()) return edge;

            List<String> places = new ArrayList<>();

            for (GraphReducer.Place place : reducer.placesAlong(edge)) places.add(place.getId());

            if (places.equals(covered.getPlaceIds())) return edge;
        }

        return null;
    }

    /**
     * The covered edge the walk goes on along from this square, or null where none goes on (OB-239).
     *
     * One that ENDS here first: the train came along it, and its places are where the body lies - the arriving copy
     * `Layout.walkOneTail` takes first, for the same reason (SVZ-B1).  Then one that starts here, which is a train
     * turned round on its square.  Never back to a square already walked.
     *
     * @param covered the train's covered edges
     * @param at the square the walk has reached
     * @param walked the squares already passed
     * @return the edge, or null
     */
    private static GraphReducer.ReducedEdge nextCoveredEdge(List<GraphReducer.ReducedEdge> covered, TileKey at,
        Set<TileKey> walked)
    {
        for (GraphReducer.ReducedEdge edge : covered)
        {
            if (at.equals(edge.getEnd()) && !walked.contains(edge.getStart())) return edge;
        }

        for (GraphReducer.ReducedEdge edge : covered)
        {
            if (at.equals(edge.getStart()) && !walked.contains(edge.getEnd())) return edge;
        }

        return null;
    }

    /**
     * A covered edge's own squares, in the order the train's body lies along them from this end - the ends excluded,
     * because the squares at either end are where trains STAND, not track lying under one.
     *
     * The ORDER is what the walk spends the train's length in: handed back the wrong way round, it would draw the far
     * end of the edge and leave the square beside the train clear.  The STEPS rather than the squares, because each
     * records which route of its square the edge runs through - which lets the diagram draw the road the train is on
     * rather than the whole tile (MT-309).
     *
     * @param rail the covered edge
     * @param from the end the walk is at
     * @return the steps, from that end
     */
    private static List<GraphReducer.TileStep> stepsFrom(GraphReducer.ReducedEdge rail, TileKey from)
    {
        List<GraphReducer.TileStep> steps = new ArrayList<>();

        for (GraphReducer.TileStep step : rail.getPath())
        {
            if (step.getTile() == null) continue;

            if (step.getTile().equals(rail.getStart()) || step.getTile().equals(rail.getEnd())) continue;

            steps.add(step);
        }

        if (from.equals(rail.getEnd())) java.util.Collections.reverse(steps);

        return steps;
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
     * @param sensor the Point's square, one end of the edge
     * @param rail the covered edge the body lies along there - whose own side at the square names the road, and not the
     *        first edge joining the two squares, which where two roads join them could be the other (OB-239)
     */
    private void markTheSensor(Map<TileKey, Set<RouteId>> out, TileKey sensor, GraphReducer.ReducedEdge rail)
    {
        Set<RouteId> roads = out.get(sensor);

        if (roads == null)
        {
            roads = new LinkedHashSet<>();

            out.put(sensor, roads);
        }

        RouteId road = roadOn(sensor, sensor.equals(rail.getEnd()) ? rail.getEntrySide() : rail.getExitSide());

        if (road != null) roads.add(road);
    }

    /**
     * Which road of a square uses this side, or null when that cannot be said.
     *
     * The side is where an edge leaves or reaches the square - so the line through a sensor and the line along the
     * track beside it describe one road, that edge's own, rather than two answers that could part.  It was asked of
     * the first reduced edge joining two squares either way round, which where two roads join them could be the other
     * road (OB-239).  A double curve's two arcs share no side, so the answer is one road even there - which is the
     * case MT-309 was about.
     *
     * @param sensor the square
     * @param side the side an edge leaves or reaches it by, or null
     * @return the road, or null
     */
    private RouteId roadOn(TileKey sensor, Side side)
    {
        if (sensor == null || side == null || graph == null) return null;

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
     * Adam: **"calculating the simple bfs path from the current station to the paste target using the current
     * direction.  paste with the direction where the train ends up at the destination.  if no path pick randomly from the
     * allowed departure destinations."**
     *
     * Which way a train ends up facing after moving between two platforms is decided by the TRACK between them and by which
     * way it was pointing when it set off (a train that leaves southbound and comes back round a loop is northbound at a
     * station one square away), so no reasoning about the two endpoints recovers it; driving it does.  The graph already
     * encodes the direction, because facing is one-way edges: the copy a train stands on IS its heading, so a
     * breadth-first walk from the copy it is on arrives at a specific COPY of the target, and that copy's side is the
     * answer.  Shortest path, because a paste is "put it there", not "take it on a tour".
     *
     * **When there is no path** the two squares may genuinely be unconnected in the direction the train faces.  The
     * heading it already has is kept where the landing can hold it (Adam: *"as long as the direction isnt
     * flipped (which it was before)"*, superseding the random pick), and otherwise the first copy a train could DEPART
     * from is taken.  A copy with no way out is never chosen: standing a train where it cannot move is the "nothing
     * moves" fault the placement menu already refuses.
     *
     * **Neither arm may answer twice differently**: putting the same train on the same square twice must give the same
     * railway.  `core.testAPasteDoesNotTurnTheTrainRound` pins it.
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

        // THE HEADING IT ALREADY HAS, WHERE THE LANDING CAN HOLD IT (Adam: *"Option 1 is fine as long as the
        // direction isnt flipped (which it was before)."*).  Option 1 is `facingAfterAPaste`'s rule, the heading survives; a
        // train that could not be DRIVEN to its landing must not be turned round at random.
        Side already = facingOf(locomotive, running);

        if (already != null && departable.contains(already)) return already;

        // And then the first legal one rather than a random legal one: two pastes of the same train onto the same square have
        // to give the same railway (Adam: "in all your simulations, state should never drift").
        return departable.get(0);
    }

    /**
     * The breadth-first half: from wherever this train is standing, to the nearest copy of the target that a train could
     * be STANDING on.
     *
     * **First-touched-wins would be a coin toss** (Adam: *"When 2-8-4 is pasted, it should always face east.  Does
     * it?"*): `Layout.getNeighbors` SHUFFLES (right for autonomy picking a journey, wrong for a question about where one
     * square lies relative to another).  Breadth-first still measures true distances through a shuffled expansion, so the
     * DISTANCES decide and only ties are left for this method to break, by rule.  And a square trains may turn round at is
     * two Points per arrival side - the plain copy, where a train that drove in is standing, and the turning copy, where
     * it is after deciding to turn - reached by the same edge, so always equally far away, and facing OPPOSITE ways.
     *
     * So the tie is broken towards the copy a train would simply be standing on: putting a train down is not a decision to
     * reverse it, and the turning copy IS that decision - the facing menu is where an operator makes it.
     *
     * **And a compulsory turn still turns.**  Adam: *"for terminuses, they must reverse on paste"*.  Where
     * turning is compulsory the builder emits no plain copy at all, so the turning copy is the answer.
     *
     * A turning copy is told from a plain one by `isTerminus() || isReversing()`, which is what the builder writes on it
     * (`stops ? "terminus" : "reversing"`).  That predicate cannot tell a turning copy from a real terminus on its own
     * (OB-205, MT-368) and need not here: at a real terminus every copy answers it, so the preference finds nothing to
     * prefer.
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

        // WHERE THE TRAIN IS, NOT WHERE IT IS MERELY RESERVED (PRV-C8): a locked path puts the locomotive on EVERY point of
        // its route, so during a run this scan could stop at a junction the train has not reached.  Nothing in the model
        // separates a reservation from a train, so the tie-break is the railway's own feedback: an occupied square has
        // something standing on it.  Where none of the matches is occupied (a stopped railway with a placed train) the
        // first match stands.
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

                // REACHED, BUT NOT DRIVEN THROUGH (PRV-B1): a turning copy is where a train is AFTER it has turned round, so a
                // route through one contains a reversal, which the runtime refuses mid-path (`reversesAlongTheWay`); a
                // distance measured through one could win the nearest-copy contest against a copy the train can really
                // reach.  The square is still recorded above, because arriving there and stopping is a good end to the
                // journey.  The square the train sets off from is expanded normally: only copies REACHED by the walk are dead
                // ends.
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
     * Which way a train cut from one square would face on another, if it DROVE there (OB-270).
     *
     * Adam: *"Trains should not inadvertently change direction when pasted, so a loc going west from bottomsecondary should
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
     * Adam: **"pasted locomotives pasted on the succeeding/preceding station to a given station on the same
     * line face into the station and away from that station, respectively"**, and **"for terminuses, they must reverse on
     * paste"**.
     *
     * **Both halves of the first sentence are one rule: the heading survives.**  The same train with the same heading,
     * one station later or earlier, faces into or away from the reference station - only the square it stands on
     * differs - so putting it down must not turn it.
     *
     * **And a terminus has one direction, so a train put down there takes it.**  That falls out of asking what the landing
     * can hold: the end of a line holds one heading, whatever the train was doing before.
     *
     * The heading has to be read BEFORE the move, from where the train was standing, and handed in here: the landing
     * copy's own side is not the train's heading, because `StationIndex.speakerAt` says that on an empty square "any copy
     * will do" (SPEC-A1).  Separated from the door so that it can be run; `testAPastedTrainKeepsItsDirection` asserts the
     * call site is a call site.
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

        // Several copies, and the heading did not survive the move: nothing here knows which the operator meant, and the copy
        // the layout picked was picked arbitrarily, so the value is cleared rather than invented (SPEC-A1) and the facing
        // menu asks.
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
     * **The setup names the square a train SET OFF FROM until `captureFromLayout` writes the arrival back** (as for
     * `flipFacing`).  It bites when a train is run and then placed by hand: the heading read "before the move" is the
     * heading it had at the platform it left, and the paste then preserves a direction from a different part of the
     * railway.
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

                // THE COPY IT IS STANDING ON, not the square's stored facing (PRW-B3): that is ONE value per square, and a square
                // is up to four Points facing two ways, so it is the setup's last word about the platform rather than about
                // this train (after a run, the previous occupant's).  Which copy a train stands on IS its direction (the model
                // has no train direction of its own; one-way edges are how facing is written down).
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
     * **The setup and the railway are two places, and the facing menu only ever wrote one.**  Adam's ruling
     * was that `flipFacing` should also update the running layout; this is the same statement from the other end, the
     * operator choosing a direction by hand.
     *
     * **The train the railway has here, and only then the setup's** (TDY4-C5).  After a run the setup still names each
     * square's pre-run occupant (behaviour.md 6a), so turning the setup's train would move nothing (it is found on no copy
     * of the square) while the train actually standing there stayed as it was.  The setup's record is its own train's, so
     * it is written only where the railway has that train, or none.
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
     * through a save and a load.
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
     * Adam: *"select the farthest sensor the tail of the train recently crossed."*  The road from that
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
     * **A square that splits is several Points, and which one a train is on IS its direction**, so this is a reading of
     * where the train is, not a lookup of something somebody typed.
     *
     * The station label reads the copy (`TrainControlUI.facingArrowOf`), because the stored value is written only when
     * somebody places a train BY HAND, so it would vanish for a train autonomy had driven there; the facing MENU must read
     * the same thing (OB-181), or the two agree only while a hand-placed train has not moved.
     *
     * Null when no copy of the square holds a train, and the caller falls back to what the setup records.
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

            // ON ITS WAY, THE COPY IT WILL STAND ON (OB-314): a train sent to a square it may turn at holds the turning copy its
            // path ends on until it arrives, and is stood on the plain copy only then, so the label must not read it the wrong
            // way round for the whole journey.
            org.traincontrol.automation.Point standing = running.copyItWillStandOn(copy.getCurrentLocomotive());

            if (standing != null && standing != copy && standing.isSamePlaceAs(copy))
            {
                Side will = getStationIndex().facingsAt(square).get(standing.getName());

                if (will != null) return will;
            }

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
     * Which way to draw a train no copy of its square holds a facing for (OB-348) - one whose copy records no facing and has
     * no single way out, or one the railway stands on no Point (`trainsOnNoPoint`, V348C-C1): the
     * facing the setup records, where its track runs that way - the one the caption shows - or else a guess along its
     * track: the first facing the square offers, which is the one the Is Facing menu ticks where it offers one (V348D-C1),
     * or where it offers none its first way, which is north or south on track that runs north to south, east or west on
     * track that runs across, and along the curve on a curve.  Null where the square has no track to ask.
     *
     * A square no track leads out of is emitted whole, with no facing, and an icon given none is drawn as its picture is
     * (east).  Adam: *"The icon can guess in that case, but it must be N/S or a N/S track, or E/W on E/W, or
     * angled on a curved track."*
     *
     * @param square the diagram square
     * @return a side its track runs to, or null
     */
    public Side facingWhereNoCopySays(TileKey square)
    {
        List<Side> track = waysTheTrackRuns(square);

        if (track.isEmpty()) return null;

        Side recorded = recordedFacingAlongTheTrack(square);

        if (recorded != null) return recorded;

        List<Side> offered = facingChoices(square);

        return offered.isEmpty() ? track.get(0) : offered.get(0);
    }

    /**
     * The facing the setup records for the train on a square, where the square's track runs that way, or null (OB-348).
     *
     * A record the track does not run - left by an edit of the track under it - is no direction a train there can face,
     * and the icon cannot be drawn along it; the caption and the Is Facing menu read this, so the three say the same.
     * Where the square's track cannot be asked, the record as it stands.
     *
     * @param square the diagram square
     * @return the recorded side, or null
     */
    public Side recordedFacingAlongTheTrack(TileKey square)
    {
        Side recorded = getFacing(square);

        if (recorded == null) return null;

        List<Side> track = waysTheTrackRuns(square);

        return track.isEmpty() || track.contains(recorded) ? recorded : null;
    }

    /**
     * The sides a square's track runs to, in the order of its routes, without repeats; empty where it cannot be asked.
     *
     * @param square the diagram square
     * @return the sides
     */
    private List<Side> waysTheTrackRuns(TileKey square)
    {
        List<Side> track = new ArrayList<>();

        Map<RouteId, Route> routes = square == null ? null : getRoutes(square);

        for (Route route : routes == null ? java.util.Collections.<Route>emptyList() : routes.values())
        {
            for (Side end : new Side[] {route.getA(), route.getB()})
            {
                if (end != null && !track.contains(end)) track.add(end);
            }
        }

        return track;
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
     * Gives a square a home locomotive, taking that locomotive's home away from anywhere else.
     *
     * ONE locomotive, ONE station.  The running layout enforces this (setHomeLocomotive clears the same locomotive from
     * every other Point as it assigns one), and the setup-side editor must too, or two squares could be given the same home
     * and the next load would drop one of them by iteration order, with a log line as the only notice (TD-8).  The sweep is
     * here rather than in the menu because it is a rule about the SETUP, and a rule enforced at one door of two is the
     * shape this defect came from.
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
     * Adam, on a home given to a train standing elsewhere: *"prompt the user for the direction"*.  A facing
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
     * `setPointProperty` re-derives the station index on every call, a full builder construction, so a bulk gesture built
     * out of `setHome` would cost one per square.  The RULE stays in one place: one locomotive, one station, swept here
     * rather than at each caller (TD-8).  `setHome` is this method plus the re-derive, and `homeEveryPlacedTrain` is this
     * method in a loop plus one re-derive at the end - one writer and two schedules.
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
     * The single-square setter re-derives the station index per call, a full builder construction on the event thread:
     * wrong for sixty-two squares ("forty tiles meaning forty full rebuilds ... for the gesture that exists precisely
     * because it is the common one", as the bulk direction setter says).  Both doors write the same property through the
     * same method, and only the re-derive differs, so the two cannot come to disagree about clearing a home.
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
     * Adam: *"Add a right click menu open to clear all max station train lengths (grouped with the other
     * clear options)"*.  Above 0 because 0 is "any length" and is what the station has after the clear: the setup
     * writes an explicit `maxTrainLength: 0` on every destination, and counting those would offer to clear stations
     * that have nothing to lose.  Every page, as Clear All Home Locomotives and Clear All Track Lengths beside it.
     *
     * **A NEGATIVE COUNTS, because it is the one the operator most needs to be able to clear** (SET-B1):
     * `Layout.fromJSON` refuses a maximum below 0 and invalidates the whole configuration, so a clear that counted only what
     * is above 0 would say there was nothing to clear on the very setting that was stopping the railway loading.  The editor
     * door no longer writes one (`AutonomyEditorPanel.whyNotThisNumber`); this removes one already written, from an older
     * build or by hand.
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
     * `tilesWithALocomotive()` reads the configuration's points directly and so answers for every page; this is
     * `placedLocomotives()`, which is what the bulk doors walk and which skips pages excluded from autonomy.  A menu that
     * COUNTS one while the action walks the other tells the operator a figure the gesture will not deliver
     * (`guard-and-affordance-same-question`).  A copy, because the map behind this is rebuilt on every call anyway.
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
     * Adam: *"to bulk tools in the autonomy editor, add an option to mass mark current train locations as their homes."*
     *
     * **The same shape as `clearEveryHome` beside it**: `setPointProperty` rebuilds the station index on every call, a full
     * builder construction on the event thread.  It writes through `writeHome`, which is that rule without the re-derive
     * (SEV-C2).  **ONE LOCOMOTIVE, ONE STATION still holds**: a train being homed HERE has to lose its home anywhere else,
     * and `writeHome` is where that rule lives - writing the property directly would be a second door with the sweep
     * missing, which is how TD-8 arrived.
     *
     * **HOW FRESH THE PLACEMENTS ARE (SEV-C4).**  This writes from the SETUP's placements, the picture the editor is
     * showing.  Autonomy cannot be running: `TrainControlUI.whyAutonomyEditorCannotOpen` refuses to open this editor while
     * `isAutonomyBusy()`, and the other windows refuse the other way round with `autolayout.errorCannotEditWhileRunning`,
     * so no run can have moved a train since the capture that opened the editor.
     *
     * **Where the railway has a train, that is where it is homed** (RSA18-B2): Bulk Tools is on the track diagram's menu
     * too, which folds nothing before a gesture, so after a run the setup still names the squares the trains set off from.
     * In the full editor, which folds on opening, the two agree.  A train the railway does not have is homed where the
     * setup has it (`trainsWhereTheyStand`).
     *
     * **A square already homed to a DIFFERENT train is overwritten**, because that is what the gesture says.  The count
     * returned is the number of squares this assigned; those whose home was already this train are included, because "it
     * is already right" and "I set it" are the same outcome to the operator.
     *
     * @return how many squares were given a home
     */
    public int homeEveryPlacedTrain()
    {
        int assigned = 0;

        // WHERE THE TRAINS STAND (RSA18-B2): the railway's, then the setup's where the railway has none of them.  Bulk
        // Tools is on the track diagram's menu too, which folds nothing first, and after a run the setup alone homed each
        // train where it had set off.
        for (Map.Entry<TileKey, String> placed : trainsWhereTheyStand().entrySet())
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
     * Every home a train can be put on: the square, and the locomotive it is home to (FR-115).
     *
     * A home the operator GAVE - the setup's `home` on a square - and not wherever a train last stood, which the
     * running layout also calls a home until the next build.  On a page autonomy runs, as placements are
     * (`placedLocomotives`).  What Place All Trains at Their Homes is counted and offered by, and what it walks, so the two
     * cannot answer differently.
     *
     * **A locomotive is offered once.**  An older or hand-edited setup can home one on two squares (the editor's own door
     * sweeps the first), and standing it on both would refuse the whole configuration.  The square kept is the one the
     * loader keeps, so the placement and the layout built from it agree (`squareTheLoaderKeeps`).
     *
     * @return the homes, square to locomotive, in no particular order
     */
    public Map<TileKey, String> homesToPlace()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();

        Map<String, List<TileKey>> squaresOf = new LinkedHashMap<>();

        for (TileKey tile : tilesWithAHome())
        {
            if (store.getExcludedPages().contains(tile.getPage())) continue;

            Object home = getPointProperty(tile, "home");

            if (home == null || home.toString().trim().isEmpty()) continue;

            out.put(tile, home.toString());

            squaresOf.computeIfAbsent(home.toString(), name -> new ArrayList<>()).add(tile);
        }

        for (Map.Entry<String, List<TileKey>> homed : squaresOf.entrySet())
        {
            if (homed.getValue().size() < 2) continue;

            TileKey kept = squareTheLoaderKeeps(homed.getValue());

            for (TileKey tile : homed.getValue())
            {
                if (!tile.equals(kept)) out.remove(tile);
            }
        }

        return out;
    }

    /**
     * Which of the squares one locomotive is homed on `Layout.rebuildHomeStations` keeps its home on.
     *
     * The loader walks its Points in the iteration order of a `HashMap` keyed by Point name, keeps the first home it meets for
     * a locomotive and drops the rest with a warning.  That order is a function of the names and of the order they were put
     * in, so it is reproduced here the same way: the names the build emits, in its order, put into a `HashMap` of their own,
     * and the first of the squares' home-carrying Points it yields.  Only a locomotive homed twice asks, because it builds
     * the configuration.
     *
     * @param squares the squares one locomotive is homed on, two or more
     * @return the one square the loader keeps; the first given where the build does not say
     */
    private TileKey squareTheLoaderKeeps(List<TileKey> squares)
    {
        try
        {
            org.json.JSONArray emitted = new org.json.JSONObject(buildConfiguration()).getJSONArray("points");

            Map<String, org.json.JSONObject> byName = new java.util.HashMap<>();

            for (int at = 0; at < emitted.length(); at++)
            {
                org.json.JSONObject point = emitted.getJSONObject(at);

                byName.put(point.getString("name"), point);
            }

            for (org.json.JSONObject point : byName.values())
            {
                if (!point.has("home") || point.optString("home", "").trim().isEmpty()) continue;

                TileKey tile = AutonomyCompanionStore.parseTileKey(point.optString("square", ""));

                if (tile != null && squares.contains(tile)) return tile;
            }
        }
        catch (org.json.JSONException notBuilt)
        {
            // no build to read, so the first one given
        }

        return squares.get(0);
    }

    /**
     * Puts every locomotive with a home on it, facing the way it was homed, and takes every other locomotive off its
     * station - in the SETUP only, nothing sent to the railway (FR-115; Adam: "Place all at their homes.
     * Teleports locomotives to their home stations, facing the correct way, and clears all other locomotives from other
     * stations (without actually moving anything)").
     *
     * **The facing** is the home's own where it was homed with one (`homeFacing`); where it was not, the one the caller
     * gives - the running layout's home copy, which the build chose for it - or none, on a square with one way to face.
     *
     * **No arrival side and no road.**  A train put down with no road it came by has no tail behind it (TDU2-A1), and an
     * unknown arrival is the safe answer: nothing is held behind the train.
     *
     * **Each train's settings travel with it** - speed, arrival functions and the rest live on the placement, and the
     * build resets whatever a placement leaves out - so a homed train standing elsewhere brings its own to its home.
     *
     * **Every train the railway or the setup has standing is named in the answer**, homed or not, because the rebuild
     * that follows (`TrainControlUI.rebuildRunningLayoutFromSetup`) puts back every train it is not told about where the
     * railway had it - which would undo this for any train it missed.
     *
     * Re-derives once, as the other bulk doors do.  Homes and placements on pages left out of autonomy are left alone,
     * as Clear All Locomotives leaves them.
     *
     * @param fallbackFacings for a home recorded without a facing, the way its locomotive should face there; may be empty
     * @return every locomotive this put on a home or took off a station
     */
    public java.util.Set<String> placeEveryTrainAtHome(Map<TileKey, Side> fallbackFacings)
    {
        Map<TileKey, String> homes = homesToPlace();

        java.util.Set<String> touched = new LinkedHashSet<>();

        for (String standing : trainsWhereTheyStand().values())
        {
            if (standing != null) touched.add(standing);
        }

        // OFF EVERY STATION, each train's settings kept to carry to its home
        Map<String, org.json.JSONObject> settings = new LinkedHashMap<>();

        for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())
        {
            Object loc = getPointProperty(placed.getKey(), "loc");

            if (loc instanceof org.json.JSONObject && placed.getValue() != null)
            {
                settings.putIfAbsent(placed.getValue(), new org.json.JSONObject(loc.toString()));
            }

            writePointProperty(placed.getKey(), "loc", null);
            writePointProperty(placed.getKey(), AutonomyBuilder.FACING, null);
            writePointProperty(placed.getKey(), "arrivedFrom", null);
            writePointProperty(placed.getKey(), "arrivedAlong", null);

            if (placed.getValue() != null) touched.add(placed.getValue());
        }

        // AND ONTO ITS HOME, facing the way it was homed
        for (Map.Entry<TileKey, String> home : homes.entrySet())
        {
            org.json.JSONObject loc = settings.containsKey(home.getValue())
                ? settings.get(home.getValue()) : new org.json.JSONObject();

            loc.put("name", home.getValue());

            writePointProperty(home.getKey(), "loc", loc);

            Side facing = sideNamed(getPointProperty(home.getKey(), AutonomyBuilder.HOME_FACING));

            if (facing == null && fallbackFacings != null) facing = fallbackFacings.get(home.getKey());

            writePointProperty(home.getKey(), AutonomyBuilder.FACING, facing == null ? null : facing.name());

            touched.add(home.getValue());
        }

        deriveStationIndex();

        return touched;
    }

    /** A side from its stored name, or null for none or a name that is not a side. */
    private static Side sideNamed(Object name)
    {
        if (name == null) return null;

        try
        {
            return Side.valueOf(name.toString().trim());
        }
        catch (IllegalArgumentException notASide)
        {
            return null;
        }
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
     * For the one gesture that is about all of them at once (R28-C1).  Adam: **"that option should be added
     * back in to the autonomy editor, with a confirmation."**
     *
     * @return the squares, in NO PARTICULAR ORDER (a `JSONObject` is a `HashMap`: stable for a given set of keys and
     *         unrelated to the order they were written, CD3-C4), empty when nothing is homed anywhere
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
     * @return the squares, in NO PARTICULAR ORDER, as `tilesWithAHome` (CD3-C4, REL-C5); empty when nothing is placed
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

    /**
     * One of a Point's operational properties, in the active configuration.
     *
     * Kept per configuration rather than beside the track, because these are what a configuration IS:
     * the same railway with different rules about where trains may stand and turn.  The keys are the
     * ones parseAuto reads, so nothing has to translate them on the way out.
     *
     * @param tile
     * @param key terminus, reversing, active, maxTrainLength, speedMultiplier, priority, home,
     *        excludedLocs, excludedLocsMode
     * @param value the value, or null to remove the property entirely
     */
    public void setPointProperty(TileKey tile, String key, Object value)
    {
        writePointProperty(tile, key, value);

        // The split names are computed from these properties - which squares turn trains round, and which are berths - so a
        // cached set of them is out of date the moment one changes.  This method deliberately does not rebuild, so without
        // this the labels would look up Point names the running graph had never heard of, and that station would stop
        // filling in until the next load.
        deriveStationIndex();
    }

    /**
     * The write on its own, for a caller that is about to make several and will re-derive once (DY3-C5).
     *
     * Deriving the station index is a full builder construction, so doing it per square would turn one press of "clear every
     * home" into sixty-two of them on the event thread; the bulk direction setter has the same shape.
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

        Set<TileKey> changed = recordAlong(path, 1, path.size() - 2);

        // AND THE RUNS OF THE TWO SQUARES CLICKED (OB-361): a square of plain track is part of a run, which is one
        // decision, and left out it left its run saying two things - the sample's 10,3.  A sensor or a switch clicked is
        // still left as it is: closing a road there would block traffic that never enters the run.
        TileKey first = path.get(0);
        TileKey last = path.get(path.size() - 1);

        changed.addAll(recordTheRunOf(first, graph.sideTowardNeighbour(first, path.get(1))));

        Route lastRoad = firstRoute(last);
        Side cameBy = graph.sideTowardNeighbour(last, path.get(path.size() - 2));

        if (lastRoad != null && cameBy != null) changed.addAll(recordTheRunOf(last, lastRoad.other(cameBy)));

        return directionsTouched() ? changed.size() : 0;
    }

    /**
     * Records one way along the run a square of plain track belongs to, the way a train leaving that square by this side
     * goes, without re-deriving (OB-361).
     *
     * @param square the square
     * @param toward the side trains leave it by
     * @return the squares recorded, none where the square is no plain track
     */
    private Set<TileKey> recordTheRunOf(TileKey square, Side toward)
    {
        TileGraph.Run run = graph.runOf(square);

        if (run == null || toward == null) return new LinkedHashSet<>();

        List<TileKey> path = new ArrayList<>();

        if (run.getStart() != null) path.add(run.getStart());

        int first = path.size();

        path.addAll(run.getTiles());

        int last = path.size() - 1;

        if (run.getEnd() != null) path.add(run.getEnd());

        int at = first + run.getTiles().indexOf(square);

        // ALONG THE PATH when the side leads to the square after it, or where nothing is before it to lead back to
        boolean along = at + 1 < path.size() ? toward == graph.sideTowardNeighbour(square, path.get(at + 1))
            : !(at > 0 && toward == graph.sideTowardNeighbour(square, path.get(at - 1)));

        if (!along)
        {
            Collections.reverse(path);

            int was = first;

            first = path.size() - 1 - last;
            last = path.size() - 1 - was;
        }

        return recordAlong(path, first, last);
    }

    /**
     * Sets one direction along a path that is already known, tile by tile, the track between its two ends.
     *
     * @param path the squares in order, boundaries included
     * @return how many tiles were changed
     */
    private int applyOneWay(List<TileKey> path)
    {
        int changed = recordAlong(path, 1, path.size() - 2).size();

        return directionsTouched() ? changed : 0;
    }

    /**
     * Records one direction along a path that is already known, tile by tile, without re-deriving.
     *
     * Separate from the two-argument form because a RUN knows its own tiles, and re-deriving them from its two ends with a
     * shortest-path search is wrong wherever two chains join the same pair (a passing loop, a double-track section): the
     * search could return the other chain and one-way track the user had not touched.
     *
     * The squares from `first` to `last` are recorded; the others are only neighbours, to say which way the path goes.  A
     * square at the very end of the path - track that simply stops, OB-361 - takes its way from its one neighbour and its
     * road.
     *
     * @param path the squares in order
     * @param first the first square recorded
     * @param last the last square recorded
     * @return the squares recorded
     */
    Set<TileKey> recordAlong(List<TileKey> path, int first, int last)
    {
        Set<TileKey> changed = new LinkedHashSet<>();

        for (int i = Math.max(first, 0); i <= last && i < path.size(); i++)
        {
            TileKey tile = path.get(i);

            Side cameFrom = i > 0 ? graph.sideTowardNeighbour(tile, path.get(i - 1)) : null;
            Side goingTo = i + 1 < path.size() ? graph.sideTowardNeighbour(tile, path.get(i + 1)) : null;

            // THE PATH'S OWN END, on a square of one road with two sides: the road's other side.  Not a square beside a
            // link's jump, which is no end and was never recorded.
            Route only = graph.getRoutes(tile).size() == 1 ? firstRoute(tile) : null;

            if (only != null && only.getA() == only.getB()) only = null;

            boolean firstOfAll = i == 0, lastOfAll = i == path.size() - 1;

            if (firstOfAll && cameFrom == null && goingTo != null && only != null) cameFrom = only.other(goingTo);
            if (lastOfAll && goingTo == null && cameFrom != null && only != null) goingTo = only.other(cameFrom);

            if (cameFrom == null || goingTo == null) continue;

            Map<RouteId, Direction> ways = new LinkedHashMap<>();

            for (Map.Entry<RouteId, Route> entry : graph.getRoutes(tile).entrySet())
            {
                Route route = entry.getValue();

                if (!route.touches(cameFrom) || !route.touches(goingTo)) continue;

                ways.put(entry.getKey(), route.getA() == goingTo ? Direction.TOWARD_A : Direction.TOWARD_B);
            }

            // ON A SWITCH, BY ITS ARMS (OB-351): the arm the run may not leave by is shut for every road through it
            recordSquare(tile, ways);

            if (!ways.isEmpty()) changed.add(tile);
        }

        return changed;
    }

    public void setTileLength(TileKey tile, int length)
    {
        store.setTileLength(tile, length);
        touched();
    }

    /**
     * Answers these squares 0 on purpose, replacing any length they had, and re-derives once.
     *
     * What Segment Length's 0 means since *"no, add a clear button"*: a 0 typed there is the same answer a 0 in Mass Assign
     * Lengths is (OB-274) - kept, not offered again, and read by every length rule but the own-tail one (OB-300) as
     * measured track of no length (TDU-C6).  Clearing a length is `setTileLength(tile, 0)`, which
     * removes the answer as well.
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
     * `touched()` is a full builder construction - a new graph, a new reduction, a new station index - and the single
     * setter calls it per square: Segment Length on a run of four would pay for four on the event thread.
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
     * **The session's bulk door, not one `setTileLength` per square**, for the reason `clearEveryHome` gives: each call
     * `touched()` rebuilds the reducer, a full builder construction on the event thread.
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
     * A pairing is one fact about two squares, and being switched off is a fact about one, so a pair whose far end was
     * disabled would read as connected on the diagram and refuse every train.  Adam: "when an active link from one page
     * pairs with a link on another, both must be marked as active. right now, the target doesn't have to be."  Enabling
     * rather than refusing: somebody pairing two links is saying they are joined, and a disabled far end is a setting made
     * earlier about a square that was not joined to anything.
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
        return sessionRuns.flowMarks();
    }

    /**
     * Every run of plain track, keyed by the tile that speaks for it - the graph's, which finds them (`TileGraph.runs`).
     *
     * @return leader tile to the run it leads
     */
    public Map<TileKey, Run> runs()
    {
        return sessionRuns.runs();
    }

    /**
     * @return every tile of every run, mapped to the tile that speaks for it
     */
    public Map<TileKey, TileKey> runLeaders()
    {
        return sessionRuns.runLeaders();
    }

    /**
     * Whether a run can be made one way at all: a lone square, or a run with track between its two ends - the squares a
     * one-way run restricts, as `applyOneWay` walks them.  A piece of track joined to nothing has no between, so setting
     * it one way changes nothing and its click says there is no path (FR-112: the diagram's pointer asks this before it
     * offers the click).
     *
     * @param leader the square a click sets
     * @return true where a one-way direction would land on something
     */
    public boolean canRunOneWay(TileKey leader)
    {
        Run run = runs().get(leader);

        if (run == null) return true;

        int path = run.getTiles().size() + (run.getStart() != null ? 1 : 0) + (run.getEnd() != null ? 1 : 0);

        return path >= 3;
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
        return sessionRuns.setRunDirection(leader, routeId, direction);
    }

    /**
     * Whether a square follows its run: a square of a run of plain track other than the first, which speaks for it.  It
     * draws no arrows of its own, in the editor and on the track diagram alike (OB-321, OB-361).
     *
     * A run's direction is stored on every square of it, and the track diagram drew every square that said otherwise
     * than the first (OB-361), which the editor does not draw.  What a run that disagrees with itself does to a train is drawn
     * at its first square instead (`runsAs`), so nothing is hidden.
     *
     * @param tile a square
     * @return true for a square of a run that is not its first
     */
    public boolean followsItsRun(TileKey tile)
    {
        TileKey leader = tile == null ? null : leadersNow.get(tile);

        return leader != null && !leader.equals(tile);
    }

    /**
     * Which way trains run along a run, as its first square's direction - the graph's answer (`TileGraph.runsAs`).
     *
     * @param leader a square
     * @return the run's direction, or null
     */
    private Direction runsAs(TileKey leader)
    {
        return graph == null ? null : graph.runsAs(leader);
    }

    /**
     * The direction a square's road is drawn with, which a click steps on from and a menu ticks: a run's first square's
     * is the run's (`runsAs`), any other square's what is stored (OB-361).
     *
     * @param tile the square
     * @param routeId its road
     * @return the direction
     */
    public Direction directionAsDrawn(TileKey tile, RouteId routeId)
    {
        Direction run = runsAs(tile);

        return run != null ? run : graph.getDirection(tile, routeId);
    }

    /**
     * Which way each route across a square runs, as marks to draw (FR-037).
     *
     * Lifted out of the autonomy editor so that the editor and the ordinary track diagram cannot come to different
     * conclusions about which way a piece of track runs.
     *
     * WHAT DID NOT COME WITH IT, deliberately: every reason the editor has for staying quiet (a square it is ignoring, the
     * greying while a signal is being picked, a tile that merely follows its run, and the flow-mark fallback that gives a
     * bare layout one arrow per run).  Those are about the editor's own gestures; on the running diagram they would either
     * say nothing or say something false.
     *
     * The hardware correction stays, because it is a fact about the railway rather than about either window: a route the
     * blades restrict is one-way whichever direction was authored.
     *
     * @param tile the square
     * @return one mark per route, never null, empty when the square has no routes or no graph
     */
    public List<TileAnnotation.Mark> directionMarks(TileKey tile)
    {
        List<TileAnnotation.Mark> marks = new ArrayList<>();

        // NONE ON TRACK NO STATION IS ON (OB-353): no train is sent there, so which way it runs says nothing
        if (graph == null || tile == null || reachesNoStation(tile)) return marks;

        for (Map.Entry<TileGraph.RouteId, TilePorts.Route> entry : getRoutes(tile).entrySet())
        {
            TilePorts.Route route = entry.getValue();

            TileGraph.Direction direction = graph.getDirection(tile, entry.getKey());

            // A route the hardware restricts is one-way whatever the user chose: the graph leaves the authored direction BOTH
            // there (see defaultDirection), but a train still cannot pass against the blades.
            //
            // **AND A SHUT ROUTE DRAWS NOTHING (VD18-B1).**  On a permanent turnout `TOWARD_A` - toward the fork - is a
            // closure rather than a direction: it permits only an entry the blades refuse.  So a road the operator had shut
            // must not be drawn with a green arrow.
            if (!TileGraph.isPassable(direction, route))
            {
                direction = TileGraph.Direction.NONE;
            }
            else if (route.getDirectedToward() != null)
            {
                direction = route.getDirectedToward() == route.getA()
                    ? TileGraph.Direction.TOWARD_A : TileGraph.Direction.TOWARD_B;
            }

            // A RUN'S FIRST SQUARE DRAWS THE RUN, as a train finds it (OB-361) - see `runsAs`
            TileGraph.Direction run = runsAs(tile);

            if (run != null) direction = run;

            marks.add(new TileAnnotation.Mark(route.getA(), route.getB(), direction));
        }

        return marks;
    }

    /**
     * What the ordinary track diagram draws on each of these squares, decided in one pass (OB-321, FR-110).
     *
     * A run of plain track is one decision, stored on every square of it, so drawing each square's arrows would put a red
     * arrow on every square of a one-way straight (OB-321).  A square that follows its run draws no arrows, as in the
     * editor, and the run's first square draws the run (`followsItsRun`, `runsAs`; OB-361).  With `allowedToo` the directions
     * trains may run are drawn in green as well (FR-110) - or on their own, since the two are chosen apart (MT-675; Adam:
     * *"allow the user to choose: restrictions, allowances, or both.  don't make them mutually exclusive."*).
     *
     * The options are the caller's, read once a pass - see the overload of `staticAnnotationFor` that takes them.
     *
     * @param squares the squares to describe
     * @param arrows whether the travel restrictions - the red arrows, and the station ingress arrows - are drawn
     * @param allowedToo whether the directions trains may run - the green arrows - are drawn
     * @return each square to its annotation, null where it has nothing to say
     */
    public Map<TileKey, TileAnnotation> staticAnnotations(java.util.Collection<TileKey> squares, boolean arrows,
        boolean allowedToo)
    {
        return sessionRuns.staticAnnotations(squares, arrows, allowedToo);
    }

    /**
     * What the setup says about one square, for drawing on the ordinary track diagram.
     *
     * Also draws the one-way restriction arrows (FR-037), on tiles that are Points and on plain track alike: a restriction
     * is a fact about TRACK - straights, curves, the squares either side of a switch - and almost none of that carries a
     * sensor, so gating on the Point test would make the option appear to do nothing.  `directionMarks` is asked before
     * the Point test for that reason, and `diagramShowsRestrictionArrows()` defaults to true.
     *
     * @param tile
     * @return the annotation, or null when this square has nothing to say
     */
    public TileAnnotation staticAnnotationFor(TileKey tile)
    {
        return staticAnnotationFor(tile, org.traincontrol.gui.TrainControlUI.diagramShowsRestrictionArrows());
    }

    /**
     * The same, with the one-way arrows option as a caller describing many squares read it once.
     *
     * The option is a read of the Windows settings, and the main window's marks asked it for every square of the
     * railway, several times per click in the autonomy editor - a fifth of the click.
     *
     * @param tile the square
     * @param arrows whether the one-way arrows are drawn, as diagramShowsRestrictionArrows() answered for this pass
     * @return the annotation, or null when this square has nothing to say
     */
    public TileAnnotation staticAnnotationFor(TileKey tile, boolean arrows)
    {
        return staticAnnotationFor(tile, arrows, arrows);
    }

    /**
     * The same, with the station ingress arrows asked apart from the direction arrows: the green arrows can be drawn
     * without the travel restrictions (MT-675), and the ingress arrows follow the restrictions.
     *
     * @param tile the square
     * @param arrows whether the direction arrows are drawn - red, green or both, as the annotation is then told
     * @param arrivals whether the station ingress arrows are drawn
     * @return the annotation, or null when this square has nothing to say
     */
    public TileAnnotation staticAnnotationFor(TileKey tile, boolean arrows, boolean arrivals)
    {
        // AND NONE WHERE A CROSSING'S SQUARES MEET (OB-322)
        return withoutTheCrossingsMiddle(tile, staticAnnotationOf(tile, arrows, arrivals));
    }

    /**
     * Leaves out the arrows where a two-square crossing's squares meet (OB-322; Adam: *"the crossings
     * consisting of 2 fixed Y's or 2 sets of static switches have a redundant set of (red/green) arrows in the middle.
     * let's hide them in that configuration for simplicity."*).  A track over the crossing runs the same way on both of
     * its squares (`record`), so the arrows at its two outer ends say it all; the two pairs where the toes meet only
     * repeat them, facing each other in the middle.  For the track diagram and the autonomy editor alike.
     *
     * @param tile a square
     * @param annotation what is drawn on it, or null
     * @return the same annotation
     */
    public TileAnnotation withoutTheCrossingsMiddle(TileKey tile, TileAnnotation annotation)
    {
        TileKey partner = graph == null || annotation == null ? null : graph.crossingPartner(tile);

        if (partner != null) annotation.withoutArrowsAt(graph.sideTowardNeighbour(tile, partner));

        return annotation;
    }

    /**
     * What `staticAnnotationFor` answers, before a crossing's middle is left out.
     *
     * @param tile the square
     * @param arrows whether the direction arrows are drawn
     * @param arrivals whether the station ingress arrows are drawn
     * @return the annotation, or null when this square has nothing to say
     */
    private TileAnnotation staticAnnotationOf(TileKey tile, boolean arrows, boolean arrivals)
    {
        if (graph == null || reducer == null) return null;

        // A link switched off is greyed here as well as in the editor: it is the one square whose being switched off is
        // invisible from the track alone (the tile art is the same either way and the route it offered simply stops
        // existing).  Before the Point test, because a link may or may not carry a sensor.
        if (isDisabledLink(tile))
        {
            return new TileAnnotation(new ArrayList<TileAnnotation.Mark>(), -1, false, null, true);
        }

        // The one-way arrows, when the operator has asked for them (FR-037), BEFORE the Point test: that test is about
        // whether a square deserves a BADGE, and a restriction is a fact about TRACK, not sensors.
        List<TileAnnotation.Mark> marks = arrows ? directionMarks(tile)
            : new ArrayList<TileAnnotation.Mark>();

        // Track that is not a Point, but is restricted.  Still null when there is nothing to say, so a diagram with the
        // option off, or no restrictions, is exactly as bare as ever.
        if (!reducer.getPoints().containsKey(tile))
        {
            return marks.isEmpty() ? null
                : new TileAnnotation(marks, -1, false, null, false, isCurveSquare(tile), false, null, arrows, null);
        }

        String name = store.getPointName(tile);

        // A plain point draws no badge on the running diagram: the tile is a feedback tile and looks like one, so a badge
        // would only repeat what the art already says.  A badge is FOR what the art cannot say: a station, trains turn round
        // here (a non-station where trains turn keeps its mark), autonomy is not using it.
        //
        // AND THE THIRD ITEM HAD NO TERM IN THIS EXPRESSION (SVN-B6, D24-C9): a square switched off that is neither a
        // station nor a turn-around must draw its cross here as the editor does, or the drawing that says "nothing may
        // pass here" would vanish while running the railway.
        boolean shut = Boolean.FALSE.equals(getPointProperty(tile, "active"));

        boolean worthABadge = store.isStation(tile) || isTurnAround(tile) || shut;

        // The marks worked out above, alongside whatever badge this Point earns.  Handed over WHOLE, with `blockedOnly`
        // below saying which of them to draw: the annotation already filters a mark down to the restricted ones for the
        // editor's restrictions view, and deciding it a second time here would let the two drawings disagree.
        //
        // Nothing here asks whether autonomy is running: `showStaticAutonomyLayer` settles that before this is called.
        // An arrow is a statement about a setup, and with no setup there is nothing to state.
        Route road = stationRoad(tile);

        return new TileAnnotation(marks, -1, false,
            !worthABadge ? null : new TileAnnotation.Badge(
                store.isStation(tile),
                store.isStation(tile) && isTurnAround(tile),
                !store.isStation(tile) && isTurnAround(tile),
                shut || !isAutoDestination(tile),
                name != null && !name.trim().isEmpty(),
                road == null ? null : road.getA(),
                road == null ? null : road.getB(),
                isTurnAround(tile) && !isMustTurnAround(tile),
                shut,
                // which way a station's track runs out, for the terminus's flat end (FR-118)
                store.isStation(tile) ? deadEndSide(tile) : null),
            false, isCurveSquare(tile), false, null,
            // RESTRICTED ONLY, which is the whole of what was asked for: "show RESTRICTION arrows".
            // A run that is open both ways is the majority of any layout and is also the default, so
            // drawing those here would put an arrow on nearly every square - which is the clutter that
            // kept directions out of this diagram to begin with.
            arrows,
            // The station ingress arrows follow the SAME switch: a chevron saying which way into a
            // platform is shut is the same kind of statement as a one-way arrow, and a switch that turned off one and not
            // the other would half work.  The editor is not touched: its arrivals are governed by its own four-way control,
            // which has a mode showing every side of every station (why `arrivalMarks` takes an `always` flag at all).
            // `false` still means only where something is actually restricted.
            arrivals ? arrivalMarks(tile, false)
                : new ArrayList<TileAnnotation.Arrival>());
    }

    /**
     * The side of a square's road whose track runs out - to a buffer stop, or where the drawn track stops - before it
     * reaches another sensor, or null where both sides go somewhere, or neither does (FR-118).  A station there is drawn
     * as a terminus flat against that side, whatever its turning setting (Adam asked *"a station with only
     * one way out becomes a terminus, correct?"* - confirmed).
     *
     * Asked of the TRACK as drawn, not of the edges built from it: a one-way run or a barred arrival leaves trains one
     * way out of a station whose track carries on, and that station keeps its own shape.  The road is the badge's,
     * `stationRoad` - on a double curve the curve with the contact, where it was the other (RSA63-C2).
     *
     * @param tile a sensor's square
     * @return the side, or null
     */
    public Side deadEndSide(TileKey tile)
    {
        if (graph == null || reducer == null || tile == null) return null;

        Route road = stationRoad(tile);

        if (road == null || road.getA() == null || road.getB() == null || road.getA() == road.getB()) return null;

        Set<TileKey> sensors = reducer.getPoints().keySet();

        boolean outA = graph.runsOutLeaving(tile, road.getA(), sensors);
        boolean outB = graph.runsOutLeaving(tile, road.getB(), sensors);

        return outA == outB ? null : outA ? road.getA() : road.getB();
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
     * The road a Point's badge is drawn on: the road its sensor's contact is on (`TilePorts.contactRoad`) - on a
     * double-curve sensor not the first road, the other curve (MT-712) - and the first road of any other square.  The
     * track diagram's badge and the editor's ask this one question.
     *
     * @param tile the square
     * @return the road, or null where the square has none in the graph
     */
    public Route stationRoad(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagramComponent square = graph == null ? null : graph.getTiles().get(tile);

        if (square != null && square.isFeedback())
        {
            Route contact = TilePorts.contactRoad(square.getType(), square.getOrientation());

            // the graph's own road on those two sides, so the badge and the graph agree about it
            Map<RouteId, Route> routes = graph.getRoutes(tile);

            for (Route road : routes == null ? java.util.Collections.<Route>emptyList() : routes.values())
            {
                if (contact != null && (road.getA() == contact.getA() && road.getB() == contact.getB()
                    || road.getA() == contact.getB() && road.getB() == contact.getA()))
                {
                    return road;
                }
            }
        }

        return firstRoute(tile);
    }

    /**
     * Whether a square's track is a curve or a double curve, with a sensor or without: the squares whose arrows lie along
     * their chords (`TileAnnotation.heading`).  A double curve's other road's arrows were aimed from the middle of the
     * badge's road, across their own rails (MT-712); on a single curve the angle is the same either way.
     *
     * @param tile the square
     * @return true for the four curved types
     */
    public boolean isCurveSquare(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagramComponent square = graph == null ? null : graph.getTiles().get(tile);

        if (square == null) return false;

        org.traincontrol.base.LayoutDiagramComponent.componentType type = square.getType();

        return type == org.traincontrol.base.LayoutDiagramComponent.componentType.CURVE
            || type == org.traincontrol.base.LayoutDiagramComponent.componentType.DOUBLE_CURVE
            || type == org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK_CURVE
            || type == org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK_DOUBLE_CURVE;
    }

    /**
     * The tile's first route: where the badge of a square that is not a sensor is drawn (`stationRoad`).
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

    void touched()
    {
        dirty = true;

        rebuild();
    }

    /**
     * Whether this session can safely judge what the diagram no longer has (DR-B10).
     *
     * "A page that is not loaded must not be judged" is enforced by this method, the store's held entries,
     * `captureFromLayout`'s pagesInPlay, and `forgetArrivalsThatNoLongerExist`'s station-index membership test; every
     * pruner added to this class needs it.
     *
     * Two ways the picture can be incomplete, with the same remedy:
     *
     *  - a page's file did not load, so its settings look exactly like settings for track that has been deleted
     *    (`CS2File` skips a page whose file will not parse or is not there, and this layout lives in OneDrive, where an
     *    unhydrated placeholder is an ordinary Tuesday);
     *  - the numbering is suspect, meaning a renumber has happened and nothing has re-keyed the setup yet, so an id cannot be
     *    trusted to say a square is gone (VD17-C4).
     *
     * The remedy is the one OB-068 established: save, but do not prune.
     *
     * @return true when everything the setup knows about is here and correctly numbered
     */
    public boolean pagesSafeToJudge()
    {
        java.util.Set<String> loadedNames = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            // A STAND-IN FOR A PAGE THAT WOULD NOT READ IS NOT A LOADED PAGE (FV3-A1).  `NSV-B3` put a blank placeholder in the
            // list so that a link tile after it still resolves to the right page.  It carries the missing page's NAME, and
            // this set is compared by name, so without this skip `save()` would reconcile against the placeholder's one text
            // tile and drop every station, point name, length, facing, signal pairing, caption and placement on the real page
            // (MT-135).  The page still stands in the list, so the link fix keeps what it gained; it simply stops claiming to
            // be loaded.
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
     * A page that did not load, or numbering caught mid-renumber, means the diagram cannot say what it no longer has -
     * everything on the missing page looks deleted.  Nothing is pruned in that state, and the Reconciliation returned reports
     * a decline instead (DR-B10, RC-C4).
     *
     * @return what reconciling found, for showing
     * @throws IOException
     */
    public AutonomyCompanionStore.Reconciliation save() throws IOException
    {
        // Every tile of EVERY page, including the excluded ones: the graph omits excluded pages, so reconciling against it
        // would make every setting on such a page look like it belonged to a deleted tile, and saving would destroy the lot.
        // Excluding a page must be reversible.
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

        // Not while the numbering is suspect: a renumber leaves every entry name-keyed to the wrong page, so their
        // coordinates are missing from the page they were attached to and dropMissing reads that as a deleted tile.
        // Nothing re-keys, and the save below rewrites "pages"
        // from the current index, which is the only evidence a renumber ever happened.  Saving still happens; it is only the
        // DELETING that waits, until the numbering is settled.
        //
        // A page this setup knows about that is NOT among the pages above is the same problem (OB-068): `CS2File` skips a
        // page whose file will not parse or is missing, deliberately and quietly (on a layout in OneDrive an unhydrated
        // placeholder is enough), so `existing` is built only from the pages that DID load, and every name, station,
        // direction, length, signal pairing and caption on the missing page would read as deleted track, be pruned and
        // written - and the next page operation would drop that page from gleisbild.cs2 too.  Nothing about that is a
        // user's doing, and three of the four doors that reach this save discard the report.
        java.util.Set<String> loadedNames = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            // THE SAME SKIP AS `pagesSafeToJudge` AND `open`, AND IT HAS TO BE HERE TOO (FV3-A3): were it missing,
            // `incomplete` would come back true from pagesSafeToJudge but `absent` would come back EMPTY, because the
            // placeholder still supplies its name here, and `Reconciliation.declined(absent)` reports `wasDeclined()` as false
            // when the list is empty.  Three of the four doors that reach this save discard the report, so the refusal would
            // be silent in exactly the case the report exists for.
            if (page.isUnreadable()) continue;

            loadedNames.add(page.getName());
        }

        java.util.List<String> absent = store.pagesNotLoaded(loadedNames);

        boolean incomplete = !pagesSafeToJudge();

        // Not logged from here: this class has no logger, deliberately - it is the model half of the setup and every message
        // about it belongs to a window.  Callers are told through the Reconciliation (DR-B10); nothing is destroyed while the
        // answer is non-empty.

        AutonomyCompanionStore.Reconciliation report = incomplete
            ? AutonomyCompanionStore.Reconciliation.declined(absent)
            : store.reconcile(existing);

        // Pruned only once the guard above has agreed the picture is complete (DR-B10): every pruner added to this class needs
        // the guard, so it is inside it, not beside it.
        if (!incomplete) forgetArrivalsThatNoLongerExist();

        store.save();

        dirty = false;

        rebuild();

        return report;
    }

    /**
     * Writes the setup out WITHOUT reconciling it against the diagram.
     *
     * For the paths that save because the diagram is being replaced underneath them, rather than because a user pressed
     * Save.  Reconciling compares the setup against the pages this session holds, and on those paths the pages are the ones
     * an editor mutated in place, which the user may have just discarded with Cancel (Cancel reverts by re-reading from
     * disk into NEW ones), so reconciling against them would delete the names, lengths, directions and arrival restrictions
     * of every square the user had deleted and then thought better of.
     *
     * Nothing is lost by waiting: the next explicit save reconciles with pages that are actually current, which is the
     * moment its report can be shown to somebody anyway.
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
     * Who stands where: the railway's trains on the squares it has them, then the setup's placements of trains the railway
     * does not have, on squares the railway leaves free (RSA18-B2).  After a run the setup still names each moved train's
     * old square - the track diagram's menu folds nothing before a gesture - so a reader of the setup alone acts on
     * squares the trains have left.
     *
     * @return square to train
     */
    public Map<TileKey, String> trainsWhereTheyStand()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();

        Map<String, Object[]> railway = railwayTrains();

        for (Map.Entry<String, Object[]> train : railway.entrySet())
        {
            out.putIfAbsent((TileKey) train.getValue()[0], train.getKey());
        }

        for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())
        {
            if (placed.getValue() == null || railway.containsKey(placed.getValue())) continue;

            out.putIfAbsent(placed.getKey(), placed.getValue());
        }

        return out;
    }

    /**
     * The squares holding a train whose length nobody has set.
     *
     * Empty when no source has been installed, which is the honest answer: not knowing a length and
     * knowing it is unset are different things, and only one of them is worth a warning.
     *
     * @return the squares and the trains standing on them, where the railway has them first (RSA18-B2, RSA19-C1)
     */
    private Map<TileKey, String> placedTrainsWithoutLength()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();

        if (trainLengths == null) return out;

        // WHERE THEY STAND, not where the last run set them off from (RSA18-B2)
        for (Map.Entry<TileKey, String> placed : trainsWhereTheyStand().entrySet())
        {
            if (placed.getValue() == null || placed.getValue().trim().isEmpty()) continue;

            Integer length = trainLengths.apply(placed.getValue());

            // Zero and unset mean the same thing here, and both mean the same to the railway: a train
            // of no length is never too long for anywhere.
            // WITH THE TRAIN THAT STANDS THERE (RSA19-C1): the warning named the setup's
            if (length == null || length <= 0) out.put(placed.getKey(), placed.getValue());
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
     * Stations only: a maximum on somewhere trains cannot stop decides nothing (FR-046).
     *
     * **Gated on the railway modelling lengths at all** (SVN-C3, REL-C2).  Adam set that condition for
     * `reversalsWithoutLength` - *"a railway that measures nothing has decided not to model lengths"* - and without it
     * every station on a layout that models no lengths would be listed.  **But not on TRACK lengths:** `maxTrainLength` is
     * compared against the locomotive's own authored length (`Point.validateTrainLength`) and no tile length enters into
     * it, so somebody who authors train lengths and station maxima and has never measured track must still get the
     * notices.  The question is "does this railway model lengths": any track measured, any station with a maximum, or any
     * locomotive with a train length.
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
     * Whether any square of a leg that takes a length has one - what makes the berth rule judge the leg at all (PRW-B1).
     * Asked by both berth notices (TDA4-C3).  A route tile is passed over, as it takes no length (OB-273): counting it
     * made TopR1ParkLong and TopR1ParkShort read as half measured when every square that takes a length was measured.
     *
     * @param squares the leg's squares
     * @return true when one is measured
     */
    private boolean anythingMeasuredOn(java.util.List<TileKey> squares)
    {
        // AND A LEG ANSWERED 0 THROUGHOUT IS MEASURED, at no length - the berth rule judges it (`Edge.isMeasured`), so an
        // approach answered 0 end to end is warned about as one given no room.
        boolean anySquare = false;
        boolean answeredThroughout = true;

        for (TileKey tile : squares)
        {
            if (getGraph() != null && takesNoLength(tile)) continue;

            anySquare = true;

            if (store.getTileLength(tile) > 0) return true;

            if (!store.isTileLengthAnswered(tile)) answeredThroughout = false;
        }

        return anySquare && answeredThroughout;
    }

    /**
     * The track a parking berth's rule spends before it stops, walking back from the berth over one arriving leg: how
     * much of it is measured, and how many of its squares have no length and no answer (OB-288, TDA-C7, TDA2-C6).
     *
     * `Layout.whyABerthCannotHoldIt` spends a train from the berth backwards, its own square first (OB-278), passes a
     * square with no length for nothing, and refuses the train once its spending reaches a square `endsTheBerthsRoom` -
     * a switch, a permanent turnout or a crossing.  Squares beyond that can neither hold the train nor refuse it, so
     * measuring them changes nothing, and neither notice about a berth counts them.
     *
     * @param squares the leg's squares in order, the berth last
     * @return {measured length before the stop, squares before it with no length, how it stops}: 1 at a square the
     *         berth rule refuses on - another road runs over it - 2 at one only the room walk ends at, 0 at none
     *         (TDA5-C1: the berth rule's figure is the berth rule's only where it refuses)
     */
    private int[] berthTrackBeforeTheStop(java.util.List<TileKey> squares)
    {
        int held = 0;
        int unmeasured = 0;

        TileKey berth = squares.isEmpty() ? null : squares.get(squares.size() - 1);

        for (int at = squares.size() - 1; at >= 0; at--)
        {
            TileKey tile = squares.get(at);

            // Not a route tile, which takes no length (OB-273).
            if (getGraph() != null && takesNoLength(tile)) continue;

            if (getGraph() != null && berth != null && endsTheBerthsRoom(tile, berth))
            {
                return new int[] {held, unmeasured, anotherRoadRunsOver(tile, berth) ? 1 : 2};
            }

            int length = store.getTileLength(tile);

            held += Math.max(0, length);

            // AN ANSWERED 0 IS NOT MISSING (Adam: "stop listing answered zeros as missing").
            if (length <= 0 && !store.isTileLengthAnswered(tile)) unmeasured++;
        }

        return new int[] {held, unmeasured, 0};
    }

    /**
     * Stations whose approach is measured in part and not in whole - the state that refuses a berth trains that fit.
     *
     * Adam, RTX-C2: *"We want clear warnings to the user"*.  With the whole approach measured the berth works;
     * half measured is the trap.
     *
     * **Why half measured refuses trains that fit.**  `Layout.whyABerthCannotHoldIt` declines to judge only when NOTHING is
     * measured, the berth's own square included (PRW-B1, OB-278).  One measured square is enough to make it judge, and it
     * then walks backwards CLAIMING each place before spending the train's length on it, so an unmeasured square (worth 0)
     * is claimed for nothing, and a train whose length is not spent on the measured squares before the switch or crossing
     * is refused, quoting the road rather than the hole in the measurements.  Since the berth's own square is spent first,
     * a train short enough to be spent there is not refused, which is why the count asks about the station's longest train
     * (OB-288, MT-552).  It is the refusing direction of a ruled rounding, so it is right.
     *
     * **Which state is ordinary.**  Mass Assign Lengths gives every switch on a page one length in a step of its own, and
     * MT-454's steps reach it by skipping pieces, so "switches measured, some pieces still at 0" is what the tool leaves
     * behind between sittings.
     *
     * Asked of the legs that ARRIVE at each station, which is what the berth walk reads.
     *
     * @return the station squares, each mapped to how many squares of its approach, before the switch or crossing that
     *         ends the berth's room, still have no length
     */
    public java.util.Map<TileKey, Integer> stationsWithAHalfMeasuredApproach()
    {
        java.util.Map<TileKey, Integer> out = new LinkedHashMap<>();

        if (reducer == null || store == null) return out;

        for (TileKey square : reducer.getPoints().keySet())
        {
            if (!store.isStation(square)) continue;

            // THE RULE EXEMPTS A STATION AUTONOMY CHOOSES (OP2-B2): `whyABerthCannotHoldIt` returns at once for a berth that
            // `isAutoDestination` (Adam's ruling, and the default for a station), so a warning about ordinary platforms would
            // be about a refusal that never happens.  What is left is the parking berths.
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

                // NOT A SIDE NO TRAIN STOPS FROM (MT-552): a train may come in that way and turn (TDA-C8), but none stops there, so
                // nothing about its track can refuse one - and the walk the rule stands for never starts from it.
                if (getBarredArrivals(square).contains(arriving.getEntrySide())) continue;

                boolean anyMeasured = false;

                // THE PATH AND THE BERTH ITSELF.  The path is the squares between the leg's two ends, endpoints excluded; the
                // end - the berth - is rail the walk spends first (OB-278), which `Layout.whyABerthCannotHoldIt` counts among
                // the measured places, so a berth measured with nothing behind it must be warned about (TDY-B2).
                java.util.List<TileKey> squares = new java.util.ArrayList<>();

                for (GraphReducer.TileStep step : arriving.getPath()) squares.add(step.getTile());

                squares.add(square);

                // MEASURED ANYWHERE ON THE LEG is what makes the rule judge it (PRW-B1) - beyond the stop included,
                // and the one question the run-in notice asks too (TDA4-C3): the two notices hand a berth to each
                // other on it, so two copies that drifted would leave a berth with neither.
                anyMeasured = anythingMeasuredOn(squares);

                if (!anyMeasured) continue;

                // NOT WHERE THE BERTH'S MEASURED TRACK BEFORE THE STOP HOLDS ITS LONGEST TRAIN (OB-288, MT-552).
                // `Layout.whyABerthCannotHoldIt` spends a train from the berth backwards, its own square first (OB-278), passes
                // a square with no length for nothing, and refuses the train only when its spending reaches track another road
                // runs over - a switch, a permanent turnout or a crossing (TDA-C7).  So a berth whose measured track before the
                // stop holds its longest train refuses none for the holes in it, and is not warned about.  Counted the walk's
                // way, in `berthTrackBeforeTheStop`: a square that takes no length is passed over, a square with no length
                // (answered 0 or not at all) is worth nothing and passed, and the stop ends the count (Adam, of
                // BottomMainPost - 1 on its own square, nothing on 22,7, 1 each on 22,8 and 22,9 - *"trains of length 3 can
                // hold there"*).  With no longest train set, any train could reach the stop.
                //
                // AND ONLY THE HOLES BEFORE THE STOP ARE COUNTED (TDA2-C6): the rule has refused by the time it gets past it,
                // so a square beyond is one the operator would measure for nothing.
                int[] before = berthTrackBeforeTheStop(squares);

                if (longest > 0 && before[0] >= longest) continue;

                if (before[1] > worst) worst = before[1];
            }

            if (worst > 0) out.put(square, worst);
        }

        return out;
    }

    /**
     * Stations told they hold more train than their track measures.
     *
     * Adam: *"Let's add an autonomy editor notice that alerts the user if a run-in is shorter than the berth length, that way they
     * can decide if it makes sense or not.  for example, a station of length 4 may have two segments of tracks on either
     * side of a switch, of length 2.  that is acceptable and its limitations are understood."*
     *
     * **The measured room is the guard's own number, not a second opinion**: `ReducedEdge.getRoomAtTheEnd()`, the track
     * from the last switch on the arriving edge to the platform, which is what `Layout.measuredRoomAtTheEndOf` counts when
     * that edge crosses a switch.  A notice quoting a number the refusal would not quote makes the reader measure the wrong
     * stretch.  For a parking berth the number is the berth rule's where that stops first - at a switch or a crossing
     * between the berth and the room walk's end, or on a leg with no switch at all (TDA2-C6, TDA3-C2) - and only where the
     * rule refuses there: something on the leg is measured, and another road runs over the square (TDA5-C1).  Where nothing
     * before that stop is measured and its squares were answered 0, the berth takes no train that way, which is a warning
     * of its own (`runInFigures`, TDA4-C2); unanswered, they are the half-measured notice's to name, and this says nothing
     * (TDA3-C1).
     *
     * **And at a platform, the figure a train is refused above, where there is one** (Adam, TDA-C10: *"Add the
     * refusing figure where there is one."*).  A train longer than the room stands across the switch as far as the measured
     * route in holds it (FR-087), so one longer than the shortest way in - from the station or turn nearest behind - is
     * refused (`refusingFigures`).  Given only where it is under the stated maximum.
     *
     * **An edge crossing no switch is skipped unless the train turns at its far end** - or, for a parking berth, a crossing
     * on it ends the berth rule's room (above).  There the guard carries on backwards through earlier edges, so this edge
     * alone bounds nothing and any number taken from it would be too small, except where the edge begins at a square trains
     * turn round on, which is where the walk stops.  `unmeasuredAfterTheLastSwitch` makes the same choice.
     *
     * **Silent where either side is missing**, which is somebody else's notice or nobody's business: no stated maximum is
     * `NO_MAX_TRAIN_LENGTH`, an unmeasured stretch is `REVERSAL_NEEDS_LENGTH` where it matters most, and a railway that
     * measures no track at all has decided not to model lengths - Adam's own condition, *"a railway that measures nothing
     * has decided not to model them"*.
     *
     * @return the station squares, each mapped to {the stated maximum, the smallest measured room} - and at a platform
     *         with a refusing figure under its maximum, that figure third
     */
    java.util.Map<TileKey, int[]> runInsShorterThanTheBerth()
    {
        return runInFigures(null, builtForInspection(), null);
    }

    /**
     * The walk behind `runInsShorterThanTheBerth`, and behind the parking berths that take no train on a way in because
     * every square before the stop there was answered 0 on purpose (Adam, TDA4-C2: *"Give it its own sentence
     * as a warning, make it sound intuitive (the effective specified length of the track is 0)"*): a warning of its own,
     * not the run-in notice's sentence for a berth that holds something.  The findings take both from one pass.
     *
     * @param givenNoRoom where to put the berths given no room, or null when nobody asks
     * @param built the railway the setup builds, for the refusing figures, or null when it will not build
     * @param named the built railway's point names to squares, or null to ask the builder
     * @return what `runInsShorterThanTheBerth` returns
     */
    private java.util.Map<TileKey, int[]> runInFigures(java.util.Set<TileKey> givenNoRoom, org.json.JSONObject built,
        java.util.Map<String, TileKey> named)
    {
        java.util.Map<TileKey, int[]> out = new LinkedHashMap<>();

        // The figure a train is refused above at each platform, off the railway the setup builds (TDA-C10).
        java.util.Map<TileKey, Integer> refusing = refusingFigures(built, named);

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

                // NOT A SIDE NO TRAIN STOPS FROM (MT-552): a train may come in that way and turn (TDA-C8), but none stops there, so
                // nothing about its track can refuse one - and the walk the rule stands for never starts from it.
                if (getBarredArrivals(square).contains(arriving.getEntrySide())) continue;

                int room = arriving.getRoomAtTheEnd();

                // A PARKING BERTH'S ROOM ENDS WHERE THE BERTH RULE STOPS (TDA2-C6): at a crossing as well as a switch.  The room
                // walk runs on past a crossing (`boundsTheRoom`), so quoting it would give room the berth rule does not give.  A
                // platform autonomy may choose is not judged by the berth rule, and keeps the room walk's number.
                //
                // AND WITH NOTHING SPENT BEFORE THE STOP (TDA3-C1), every train is refused there - once anything on the
                // approach is measured, which the rule asks first (PRW-B1), and only where the stop is one the berth rule
                // refuses on: another road runs over it (TDA5-C1) - so the room walk's figure is not quoted either.  Squares
                // nobody has answered are the half-measured notice's to name; answered 0 on purpose, they are the berth's own
                // warning's (TDA4-C2); this notice says nothing for either.
                boolean theBerthRulesFigure = false;

                if (!isAutoDestination(square))
                {
                    java.util.List<TileKey> squares = new java.util.ArrayList<>();

                    for (GraphReducer.TileStep step : arriving.getPath()) squares.add(step.getTile());

                    squares.add(square);

                    int[] before = berthTrackBeforeTheStop(squares);

                    if (before[2] == 1 && anythingMeasuredOn(squares) && (room <= 0 || before[0] < room))
                    {
                        if (before[0] == 0 && before[1] > 0) continue;

                        room = before[0];
                        theBerthRulesFigure = true;

                        // NOTHING BEFORE THE STOP, ANSWERED SO (TDA4-C2): the berth takes no train that comes in this
                        // way - a warning of its own, not this notice's "that may be right".
                        if (room == 0)
                        {
                            if (givenNoRoom != null) givenNoRoom.add(square);

                            continue;
                        }
                    }
                }

                if (room == Integer.MIN_VALUE)
                {
                    // No switch on this edge, so the guard keeps walking back - unless the train turns
                    // round where this edge starts, which is where it stops instead.
                    if (!isTurnAround(arriving.getStart())) continue;

                    room = arriving.getLength();
                }

                // UNMEASURED IS UNKNOWN, NOT SHORT - the doctrine the guard itself follows, and `-1` is
                // the reduction's word for "bounded, and nothing in it is measured".  Not the berth rule's 0, which is
                // what it refuses on (TDA3-C1).
                if ((room <= 0 && !theBerthRulesFigure) || room >= max) continue;

                if (worst < 0 || room < worst) worst = room;
            }

            // AND AT A PLATFORM, THE SHORTEST WAY IN (TDA-C10): a train longer than that, setting off from the station or
            // turn nearest behind, is refused where the notice says it stands across the switch.
            Integer refusedAbove = isAutoDestination(square) ? refusing.get(square) : null;

            if (worst >= 0)
            {
                // NEVER UNDER {3}, since both are taken over the same ways in (ADA2-C2), and given where it EQUALS {3}: the
                // shortest way in holds nothing behind its switch, and there a train longer than {3} is refused instead of
                // standing across it - TopMainR1Inter on his railway, the platform TDA-C10 was asked about.
                out.put(square, refusedAbove != null && refusedAbove > 0 && refusedAbove < max
                    ? new int[] {max, worst, refusedAbove} : new int[] {max, worst});
            }
        }

        return out;
    }

    /**
     * The figure a train is refused above at each platform, read off the railway the setup builds (TDA-C10): the least,
     * over the notice's own ways in, of what `Layout.whyTooLongForThisRoute` refuses above on each - the larger of the
     * room past the switch and the measured route in (`Layout.measuredRouteIn`, FR-087).
     *
     * **Off the built railway, not the squares.**  A square's copies are its directions, and which of them a train is started
     * at is the build's answer: a square that is a station can have a copy heading this way that trains may not arrive at,
     * and nobody is started there.
     *
     * **The notice's own ways in, and only where the railway refuses on them** (ADA2-C1, ADA2-C2): a leg over a switch with
     * something measured past it, or a leg with a length straight from a copy trains turn at - the ways `{3}` is taken over
     * (`roomTheNoticeQuotes`), so the figure is never less than `{3}`.  A way in with nothing measured past its switch is
     * the half-measured notice's; one with no length at all is not judged by the room rule and refuses no train.
     *
     * **The route in, walked back as the railway runs it** (`routesIn`): over sensors nobody is started at, to a copy a train
     * is started at or turns at; stopped at a leg nobody measured, and on past one answered 0 throughout, adding nothing;
     * never through or into a point that is switched off, though from a station switched off round a
     * train standing there; and never from or through another copy of the platform's own square (ADA2-C3).
     *
     * @param built the built railway, or null
     * @param named its point names to squares, or null to ask the builder
     * @return the squares, each mapped to the smallest such figure, empty when the setup does not build
     */
    private java.util.Map<TileKey, Integer> refusingFigures(org.json.JSONObject built, java.util.Map<String, TileKey> named)
    {
        java.util.Map<TileKey, Integer> out = new java.util.HashMap<>();

        if (built == null || !built.has("points") || !built.has("edges")) return out;

        java.util.Map<String, TileKey> byName = named != null ? named : builder(null).tilesByName();

        // Where a train stops, where the route in stops - a train started there, or turned there - and where nothing runs.
        java.util.Set<String> stops = new java.util.HashSet<>();
        java.util.Set<String> setsOff = new java.util.HashSet<>();
        java.util.Set<String> turns = new java.util.HashSet<>();
        java.util.Set<String> off = new java.util.HashSet<>();

        org.json.JSONArray points = built.getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.getJSONObject(i);

            String name = point.getString("name");

            // SWITCHED OFF, NOTHING PASSES OR ARRIVES (ADA2-C3): `isPathClear` refuses a route through a point that is not
            // active, or into one - and not out of one (Adam: *"Just not inactive start points.  Inactive really
            // means nothing can pass."*).
            if (!point.optBoolean("active", true)) off.add(name);

            if (point.optBoolean("station", false))
            {
                stops.add(name);
                setsOff.add(name);
            }

            // THE ROUTE IN STOPS AT A TURN (ADD2-C5): `measuredRouteIn` counts nothing behind a copy the train turned at -
            // on his railway a barred turning copy, which is no station, at RampDown and BottomMainPost.
            if (point.optBoolean("reversing", false)) setsOff.add(name);

            if (point.optBoolean("reversing", false) || point.optBoolean("terminus", false)) turns.add(name);
        }

        // The legs the railway can run, and the notice's own ways into each platform.
        java.util.List<org.json.JSONObject> legs = new java.util.ArrayList<>();
        java.util.Map<TileKey, java.util.List<org.json.JSONObject>> waysIn = new java.util.LinkedHashMap<>();

        org.json.JSONArray edges = built.getJSONArray("edges");

        for (int i = 0; i < edges.length(); i++)
        {
            org.json.JSONObject edge = edges.getJSONObject(i);

            // Into a switched-off point, no leg runs; out of one, only the first - nothing comes into it to go on.
            if (off.contains(edge.getString("end"))) continue;

            legs.add(edge);

            if (!stops.contains(edge.getString("end"))) continue;

            TileKey square = byName.get(edge.getString("end"));

            if (square != null && roomTheNoticeQuotes(edge, turns) > 0)
            {
                waysIn.computeIfAbsent(square, k -> new java.util.ArrayList<>()).add(edge);
            }
        }

        for (java.util.Map.Entry<TileKey, java.util.List<org.json.JSONObject>> platform : waysIn.entrySet())
        {
            java.util.Map<org.json.JSONObject, Integer> routeIn = routesIn(platform.getKey(), byName, legs, stops, setsOff);

            for (org.json.JSONObject edge : platform.getValue())
            {
                Integer walked = routeIn.get(edge);

                // No train comes in this way.  A leg with no length never gets here: it quotes no room (ADA2-C1).
                if (walked == null) continue;

                out.merge(platform.getKey(), Math.max(roomTheNoticeQuotes(edge, turns), walked), Math::min);
            }
        }

        return out;
    }

    /**
     * The room `{3}` is taken over for a leg into a platform - `runInFigures`' own choice - or none where that notice does
     * not read the leg: past a switch, what is measured there, -1 where nothing is; straight from a copy trains turn at,
     * the leg's length; otherwise 0, because the room walk keeps walking back and this leg alone bounds nothing.
     *
     * @param leg the built leg
     * @param turns the copies trains turn at
     * @return the room, 0 or less where it gives none
     */
    private static int roomTheNoticeQuotes(org.json.JSONObject leg, java.util.Set<String> turns)
    {
        if (leg.has("roomAtTheEnd")) return leg.optInt("roomAtTheEnd", -1);

        return turns.contains(leg.getString("start")) ? leg.optInt("length", 0) : 0;
    }

    /**
     * Whether a built leg was answered 0 on purpose at every place - measured track of no length, as `Edge.isMeasured`
     * reads the same leg on the railway.
     *
     * @param leg the built leg
     * @return true when it has places and every one is answered
     */
    private static boolean answeredThroughout(org.json.JSONObject leg)
    {
        org.json.JSONArray places = leg.optJSONArray("places");

        if (places == null || places.length() == 0) return false;

        for (int i = 0; i < places.length(); i++)
        {
            if (!places.getJSONObject(i).optBoolean("answered", false)) return false;
        }

        return true;
    }

    /**
     * The least the route in counts for a train coming along each built leg towards this platform -
     * `Layout.measuredRouteIn` over every way the railway can run it - for the legs a train can come along (TDA-C10).
     *
     * **Worked out for every leg together, and lowered until nothing changes**, so a loop of sensors is walked whole: walked
     * one leg at a time and remembered, a leg first reached inside a loop would be stored as having no way in (ADA2-C3).
     *
     * - From a copy a train is started at or turns at, the leg alone: the route in stops there.
     * - A leg nobody measured ends the route in, so nothing behind it counts: 0.  One answered 0 throughout is measured
     *   track of no length (`answeredThroughout`, as `Edge.isMeasured` asks it), and is passed like any other.
     * - Otherwise the leg and the least way into where it starts.
     *
     * Only from where a train can be got to at all without passing the platform, and never from or through another copy
     * of its own square: a route from a platform round to itself is no journey (ADA2-C3).
     *
     * @param platform the platform's square
     * @param byName the built railway's point names to squares
     * @param legs the legs the railway can run
     * @param stops the station copies, where a train can be started
     * @param setsOff where the route in stops: a station copy, or a copy trains turn at
     * @return each leg a train can come along, to the units
     */
    private static java.util.Map<org.json.JSONObject, Integer> routesIn(TileKey platform,
        java.util.Map<String, TileKey> byName, java.util.List<org.json.JSONObject> legs, java.util.Set<String> stops,
        java.util.Set<String> setsOff)
    {
        java.util.Set<String> own = new java.util.HashSet<>();

        for (java.util.Map.Entry<String, TileKey> point : byName.entrySet())
        {
            if (platform.equals(point.getValue())) own.add(point.getKey());
        }

        java.util.Map<String, java.util.List<org.json.JSONObject>> into = new java.util.HashMap<>();
        java.util.Map<String, java.util.List<String>> onward = new java.util.HashMap<>();

        for (org.json.JSONObject leg : legs)
        {
            into.computeIfAbsent(leg.getString("end"), k -> new java.util.ArrayList<>()).add(leg);
            onward.computeIfAbsent(leg.getString("start"), k -> new java.util.ArrayList<>()).add(leg.getString("end"));
        }

        // Where a train can be got to: onward from a station, never through the platform.
        java.util.Set<String> reached = new java.util.HashSet<>();
        java.util.Deque<String> todo = new java.util.ArrayDeque<>();

        for (String stop : stops)
        {
            if (!own.contains(stop) && reached.add(stop)) todo.push(stop);
        }

        while (!todo.isEmpty())
        {
            for (String next : onward.getOrDefault(todo.pop(), java.util.Collections.<String>emptyList()))
            {
                if (!own.contains(next) && reached.add(next)) todo.push(next);
            }
        }

        java.util.Map<org.json.JSONObject, Integer> way = new java.util.IdentityHashMap<>();

        boolean lowered = true;

        while (lowered)
        {
            lowered = false;

            for (org.json.JSONObject leg : legs)
            {
                String from = leg.getString("start");

                if (own.contains(from) || !reached.contains(from)) continue;

                int length = leg.optInt("length", 0);

                Integer least;

                if (length <= 0 && !answeredThroughout(leg))
                {
                    least = 0;
                }
                else if (setsOff.contains(from))
                {
                    least = length;
                }
                else
                {
                    Integer before = null;

                    for (org.json.JSONObject previous : into.getOrDefault(from,
                        java.util.Collections.<org.json.JSONObject>emptyList()))
                    {
                        Integer known = way.get(previous);

                        if (known != null && (before == null || known < before)) before = known;
                    }

                    least = before == null ? null : length + before;
                }

                Integer had = way.get(leg);

                if (least != null && (had == null || least < had))
                {
                    way.put(leg, least);

                    lowered = true;
                }
            }
        }

        return way;
    }

    /**
     * Stations every train must turn round at, reached from more than one side (MT-361).
     *
     * Adam: *"terminuses (stations that must reverse) are currently allowed to have ingress
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
     * (An arrival through a portal arrives by a real side - AMG-C2.)
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
     * The sensor addresses that sensor squares on two or more places share, each to its squares in reading order (FR-139).
     *
     * A place is a square, counted once: the squares are read off the diagram's pages and not the copies they build into,
     * so a square that builds into two Points is one place.  A page left out of autonomy has no places, and an address of
     * zero or less is no address, as `repeatedSensorPages` and the reduction both read it.
     *
     * Nor has a page `repeatedSensorPages` reports: it repeats another page's sensors, which is an ERROR already (OB-150),
     * and the same thing is not said twice (Adam: "suppress the warning where OB-150 applies").  So a share
     * between two pages is OB-150's, and this one is about squares that share an address on a page of their own.
     *
     * @return each shared address, to its squares
     */
    private Map<Integer, List<TileKey>> sensorAddressesSharedByPlaces()
    {
        Map<Integer, Set<TileKey>> squaresByAddress = new LinkedHashMap<>();

        if (pages == null) return new LinkedHashMap<>();

        Set<String> repeating = new java.util.HashSet<>();

        for (TileKey square : repeatedSensorPages().keySet()) repeating.add(square.getPage());

        for (LayoutDiagram page : pages)
        {
            if (store.getExcludedPages().contains(page.getName()) || repeating.contains(page.getName())) continue;

            for (LayoutDiagramComponent component : page.getAll())
            {
                if (component == null || !component.isFeedback()) continue;

                int address = component.getRawAddress();

                if (address <= 0) continue;

                squaresByAddress.computeIfAbsent(address, k -> new LinkedHashSet<>())
                    .add(new TileKey(page.getName(), component.getX(), component.getY()));
            }
        }

        java.util.Comparator<TileKey> readingOrder = java.util.Comparator.comparing(TileKey::getPage)
            .thenComparingInt(TileKey::getY).thenComparingInt(TileKey::getX);

        Map<Integer, List<TileKey>> shared = new LinkedHashMap<>();

        for (Map.Entry<Integer, Set<TileKey>> entry : squaresByAddress.entrySet())
        {
            if (entry.getValue().size() < 2) continue;

            List<TileKey> squares = new ArrayList<>(entry.getValue());

            squares.sort(readingOrder);

            shared.put(entry.getKey(), squares);
        }

        return shared;
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
        // KEYED BY THE SQUARE, so the finding can take the user to it (MT-223).
        Map<TileKey, String> repeats = new LinkedHashMap<>();

        if (pages == null) return repeats;

        // WHICH sensor, and where it was first seen (MT-223; Adam: "the error about a duplicate s88 does not specify which one
        // is the duplicate").
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
                    // One per repeated sensor: three repeats on one page are three things to fix, and rolled into one line,
                    // fixing two of them changes nothing about the message.  The square on the REPEATING page is the copy the
                    // user is being asked to do something about; the one that had the address first is innocent, and is named in
                    // the message rather than jumped to.
                    repeats.put(new TileKey(page.getName(), component.getX(), component.getY()),
                        I18n.f("autosetup.ui.duplicateSensorSubject",
                            sensor, page.getName(), seen.get(sensor)));

                    // AND THE PAGE STOPS COUNTING, as `excludeRepeatedSensorPages` does (LE2-B8, MT-223): a repeating page is
                    // shut there and contributes nothing further, so a third page sharing one of its other sensors is not
                    // blamed against it - that is an ERROR, and it would refuse the whole setup over a page nothing was wrong
                    // with.  FLAGGED, NOT BROKEN OUT OF: stopping the scan would cost the rest of the page (Adam's page 3 is a
                    // zoomed view of page 1 and clashes on many sensors, and each is reported).
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
