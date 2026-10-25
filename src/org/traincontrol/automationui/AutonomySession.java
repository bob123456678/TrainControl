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
 * One layout's autonomy setup, from the files on disk to the graph a train can run on: store, tile graph, reduction
 * and generated configuration.  Every edit goes through here and re-derives, so a screen never disagrees with the
 * railway.  Headless, so the editor and the viewer share one object and it is testable without a screen.
 *
 * @author Adam
 */
public class AutonomySession
{
    final AutonomyCompanionStore store;

    private List<LayoutDiagram> pages = new ArrayList<>();
    TileGraph graph;
    // Volatile, and handed out only once reduced (OB-298); see `rebuild`.
    volatile GraphReducer reducer;

    boolean dirty = false;

    /** Imports a legacy autonomy.json or an exported bundle into this session. */
    private final SessionImport sessionImport = new SessionImport(this);

    /** The runs of plain track, a direction set along one, and the marks drawn for them. */
    private final SessionRuns sessionRuns = new SessionRuns(this);

    public AutonomySession(File layoutFolder)
    {
        this.store = new AutonomyCompanionStore(layoutFolder);
    }

    public AutonomyCompanionStore getStore()
    {
        return store;
    }

    /** Whether this layout can hold a setup: autonomy is local-layout only, its files live beside the diagram. */
    public boolean isUsable()
    {
        return store.isUsable();
    }

    public boolean exists()
    {
        return store.exists();
    }

    /** Whether there are unsaved edits; the editor saves on close, so this decides whether closing asks. */
    public boolean isDirty()
    {
        return dirty;
    }

    /**
     * A page was renamed since these page objects were handed over: the store is rekeyed but these LayoutDiagrams are
     * not, so `captureFromLayout` refuses until the next `open`.
     */
    private boolean pagesStale = false;

    /**
     * Squares moved, removed or built over since the railway was built, which names them as they were: no fold until
     * `open`.
     */
    private boolean squaresMoved = false;

    /**
     * A page renamed: the setup's squares on it, and the railway's.
     * @param from the page's name as it was
     * @param to its name now
     */
    public void renamePage(String from, String to)
    {
        store.renamePage(from, to);

        markPagesStale();

        moveTheRailwaysSquares(square -> from.equals(square.getPage()) ? new TileKey(to, square.getX(), square.getY())
            : square);

        // Until the next `open` these pages still carry the old name, and a build names its squares by it
        // (`pagesStale`)
    }

    /**
     * Moves the railway's squares with the setup's: a load finds each train by the square the railway says it is on.
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

    /** What an edit of the track changes outside its page's setup; each is put back with the setup (undo, Cancel). */
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

    /** The carry and the railway's squares as the edit began, for a Cancel; taken at the first move only. */
    private Remembered asTheEditBegan;

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
     * Puts back what was remembered and, if asked, the stored legs: a road's by square and step, the timetable's by
     * entry and leg.  Never by position, which writes roads whose squares share a bucket into each other.
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
     * A page deleted: its squares forgotten in every configuration, and these pages marked stale, so the next fold
     * cannot write the deleted page's trains back under its name.
     * @param page the page's name
     * @return how many entries were forgotten
     */
    public int deletePage(String page)
    {
        int forgotten = store.deletePage(page);

        markPagesStale();

        return forgotten;
    }

    /** Marks these page objects as no longer describing the setup (a page was renamed or deleted); `open` clears it. */
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
        this.pagesStale = false;
        this.squaresMoved = false;

        this.pages = diagrams == null ? new ArrayList<LayoutDiagram>() : new ArrayList<>(diagrams);

        Map<String, String> pageIds = new LinkedHashMap<>();

        for (LayoutDiagram page : pages)
        {
            // Not offered to the store: a yes from `pageIsHere` would release the entries OB-067 holds, unwritten.
            if (page.isUnreadable()) continue;

            if (page.getPageId() != null) pageIds.put(page.getName(), page.getPageId());
        }

        store.setPageIds(pageIds);
        store.load();

        rebuild();

        // Every square runs as its arrows show (OB-351), every run one way (OB-361); saved at once so each is said once
        broughtToTheirArrows = sentencesForWhatWasBrought();
        arrowsSaveFailure = null;
        arrowsSaveFailureToSay = false;

        if (!broughtToTheirArrows.isEmpty())
        {
            try
            {
                store.save();

                for (Map.Entry<TileKey, String> said : broughtToTheirArrows.entrySet())
                {
                    said.setValue(said.getValue() + "  " + I18n.t("autosetup.log.followsItsArrowsSaved"));
                }
            }
            catch (IOException e)
            {
                // Not saved: the next open brings them again and says so
                arrowsSaveFailure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                arrowsSaveFailureToSay = true;
            }
        }

        foldedRouteTiles = foldRouteTileLengths();

        if (!foldedRouteTiles.isEmpty()) rebuild();

        // Failures are kept for the UI, not thrown: an unwritable page is no reason to refuse the setup.
        migratedPages.clear();
        migratedCaptions = 0;

        migrationFailures = migrateStationLabels();

        dirty = false;
    }

    /** The squares brought to their arrows as this setup was opened (OB-351), each to a sentence for the log. */
    private Map<TileKey, String> broughtToTheirArrows = new LinkedHashMap<>();

    /**
     * The squares whose directions now run as their arrows show, each to a sentence for the log.  Adam: *"Align it so
     * that the arrows tell the full story."* (OB-351)
     * @return square to sentence, empty when every square already ran as its arrows show
     */
    public Map<TileKey, String> getSquaresBroughtToTheirArrows()
    {
        return Collections.unmodifiableMap(broughtToTheirArrows);
    }

    /** @return why the setup could not be saved with squares brought to their arrows, or null */
    public String getArrowsSaveFailure()
    {
        return arrowsSaveFailure;
    }

    private String arrowsSaveFailure;

    private boolean arrowsSaveFailureToSay;

    /**
     * The sentences for the squares brought to their arrows, handed over once.
     * @return one sentence per square, empty when there is nothing new to say
     */
    public List<String> takeSquaresBroughtToTheirArrows()
    {
        List<String> out = new ArrayList<>(broughtToTheirArrows.values());

        // Or that it could not be saved that way, with the reason
        if (arrowsSaveFailureToSay)
        {
            out.add(I18n.f("autosetup.log.followsItsArrowsNotSaved", arrowsSaveFailure));

            arrowsSaveFailureToSay = false;
        }

        broughtToTheirArrows = new LinkedHashMap<>();

        return out;
    }

    /**
     * A sentence per switch square brought to its arrows (OB-351), and one per run brought to one direction (OB-361).
     */
    private Map<TileKey, String> sentencesForWhatWasBrought()
    {
        Map<TileKey, String> out = sentencesFor(graph.getNormalizedToArms().keySet());

        for (Map.Entry<TileKey, Map<TileKey, Map<RouteId, Direction>>> brought : graph.getNormalizedRuns().entrySet())
        {
            TileKey leader = brought.getKey();

            // Not a square just built over taking its run's direction: that is the edit itself (OB-361)
            if (joiningTheirRuns.containsAll(brought.getValue().keySet())) continue;

            Run run = graph.runs().get(leader);

            Direction now = graph.runsAs(leader);

            if (run == null || now == null) continue;

            List<TileKey> tiles = run.getTiles();

            TileKey last = tiles.get(tiles.size() - 1);

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

    /** For each square: what it is, where, and the arms trains may now leave it by. */
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

    private Map<TileKey, TileKey> foldedRouteTiles = new LinkedHashMap<>();

    /** @return route tile to the square that took its length as this setup was opened, empty when none */
    public Map<TileKey, TileKey> getFoldedRouteTiles()
    {
        return java.util.Collections.unmodifiableMap(foldedRouteTiles);
    }

    /**
     * Moves any length a route tile holds onto the track beside it, keeping the total: a route tile takes no length
     * (OB-273) but the room and tail sums still add it.  Plain track before a switch (whose length is the page's
     * turnout length), never a sensor (its length is the last place of every approach).  No neighbour: it keeps its
     * length.
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

                if (track == null || track.isFeedback()) continue;

                if (into == null) into = beside;

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
     * Every caption on one page, as a snapshot, for `LayoutEditor.forgetCaptionsOutsideThePage`.
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

    /** Drops arrival restrictions naming sides the square no longer has, so an edit back to that shape revives none. */
    private void forgetArrivalsThatNoLongerExist()
    {
        for (Map.Entry<TileKey, Set<Side>> entry
            : new LinkedHashMap<>(store.getBarredArrivals()).entrySet())
        {
            // Only squares this setup can see: excluding a page must be reversible, so its restrictions stay
            if (!getStationIndex().squares().contains(entry.getKey())) continue;

            Set<Side> live = new LinkedHashSet<>(entry.getValue());

            live.retainAll(arrivalSides(entry.getKey()));

            if (live.size() != entry.getValue().size())
            {
                store.setBarredArrivals(entry.getKey(), live);
            }
        }
    }

    /** The pages the caption migration could not rewrite. */
    private List<String> migrationFailures = new ArrayList<>();

    public List<String> getMigrationFailures()
    {
        return Collections.unmodifiableList(migrationFailures);
    }

    /** The pages it did rewrite, to report: `saveChanges` keeps a `.cs2.bak` the first time. */
    private List<String> migratedPages = new ArrayList<>();

    public List<String> getMigratedPages()
    {
        return Collections.unmodifiableList(migratedPages);
    }

    private int migratedCaptions;

    public int getMigratedCaptions()
    {
        return migratedCaptions;
    }

    /**
     * Throws away every edit since the last save by re-reading the setup: edits go straight into the live
     * configuration.
     * @throws IOException if the setup cannot be re-read, in which case nothing is changed
     */
    public void discardEdits() throws IOException
    {
        store.load();

        rebuild();

        dirty = false;
    }

    /**
     * Creates a configuration; the first one for a layout that has none.
     * @param configurationName what to call it
     * @throws IOException
     */
    public void initialize(String configurationName) throws IOException
    {
        // A name already in use throws; the caller says so.
        store.createConfiguration(
            configurationName == null || configurationName.trim().isEmpty()
                ? "Default" : configurationName.trim(),
            null);

        rebuild();
        save();
    }

    /** Re-derives everything from the diagram and the stored decisions; runs after every edit. */
    public final void rebuild()
    {
        // Not nulled: a feedback-thread reader (updateStationLabels) sees the old immutable index until the new one is
        // out
        graph = new TileGraph(pages, store.getExcludedPages());

        store.applyTo(graph);

        // A square brought to its arrows is written back to the setup (OB-351), as is each square of a run (OB-361)
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

        // Said and marked unsaved whichever door brought them in (OB-351): an import waits for
        // `takeSquaresBroughtToTheirArrows` like `open`
        if (!brought.isEmpty())
        {
            broughtToTheirArrows.putAll(sentencesForWhatWasBrought());

            dirty = true;
        }

        graph.validatePortals();

        // Handed out only once reduced (OB-298): a build asking mid-reduce would walk half-filled lists.  Every rebuild
        // runs on the event thread.
        GraphReducer fresh = new GraphReducer(graph, store.asAuthored());

        fresh.reduce();

        reducer = fresh;

        deriveStationIndex();

        // Once a build, not once a square painted (OB-353)
        stationless = squaresNoStationReaches(graph);

        leadersNow = runLeaders();
    }

    /** Track no station can be reached along, as the latest build found it (OB-353). */
    private volatile Set<TileKey> stationless = Collections.emptySet();

    /** Every square of a run to the square that speaks for it, as the latest build found them (OB-361). */
    private volatile Map<TileKey, TileKey> leadersNow = Collections.emptyMap();

    /**
     * Whether a square is track that reaches no station, however its switches lie: its directions are not drawn and it
     * is greyed.  Adam: *"make it so that arrows are hidden from track segments not connected (naively) to any
     * station."* (OB-353)
     * @param tile a square
     * @return true where no station is on its track; false everywhere while the setup has no station at all
     */
    public boolean reachesNoStation(TileKey tile)
    {
        return tile != null && stationless.contains(tile);
    }

    /**
     * Squares of each piece of track with no station; none while no square is a station, so a new setup is not grey.
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

            // A link's way through, to its partner, is the side no grid square has
            sides.add(null);

            for (Side side : sides)
            {
                Landing landing = built.landing(tile, side);

                if (landing == null || landing.getTile().equals(tile)) continue;

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

    /** Every Point name a build has emitted, to its square, so a leg naming one now gone can follow the square. */
    private final Map<String, TileKey> squareOfNameSeen = new java.util.HashMap<>();

    /** And the square's own name in that build, which the rest of the Point's name follows. */
    private final Map<String, String> baseOfNameSeen = new java.util.HashMap<>();

    /** And the way a train standing on it faced, where that build said. */
    private final Map<String, Side> facingOfNameSeen = new java.util.HashMap<>();

    private Map<String, TileKey> squareOfNameNow = Collections.emptyMap();

    private Map<TileKey, String> nameOfSquareNow = Collections.emptyMap();

    private Map<String, Side> facingOfNameNow = Collections.emptyMap();

    private Set<String> turningNow = Collections.emptySet();

    /** Its Points, to their squares' own names, which a carried leg follows (OB-343). */
    private Map<String, String> baseOfNameNow = Collections.emptyMap();

    /** Names of the edit's last build that Cancel put in the carry's memory (OB-343); forgotten when the edit ends. */
    private final Map<String, TileKey> keptSquares = new java.util.HashMap<>();

    private final Map<String, String> keptBases = new java.util.HashMap<>();

    /**
     * The name the latest build gives an old Point, found by its square and facing: the name itself if still that
     * square's, else the same copy under the square's name now, else the copy facing the same way, a turning copy last;
     * none if no copy faces that way.  By square, not name, as a renamed station can take another's name (OB-183).
     */
    public String pointNamedNow(String name, String square, String facing)
    {
        TileKey at = square == null ? null : AutonomyCompanionStore.parseTileKey(square);

        if (at == null) return pointNamedByName(name);

        if (name == null) return null;

        if (at.equals(squareOfNameNow.get(name))) return name;

        String own = nameOfSquareNow.get(at);

        // The square gone: none, and not the name, which may be another square's now
        if (own == null) return null;

        String base = baseOfNameSeen.get(name);

        if (base != null && name.startsWith(base))
        {
            String same = own + name.substring(base.length());

            if (at.equals(squareOfNameNow.get(same))) return same;
        }

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

        // A square of one copy that records no facing: that copy
        if (copies == 1 && facingOfNameNow.get(only) == null) return only;

        // No copy faces its way: none, since the copy is the direction; the check reports the train as an error
        return null;
    }

    private static Side sideNamed(String name)
    {
        for (Side side : Side.values())
        {
            if (side.name().equals(name)) return side;
        }

        return null;
    }

    /** The same by name alone, for a railway that does not say which square each Point copies. */
    private String pointNamedByName(String name)
    {
        if (name == null || squareOfNameNow.containsKey(name)) return name;

        TileKey square = squareOfNameSeen.get(name);
        String base = baseOfNameSeen.get(name);
        String own = square == null ? null : nameOfSquareNow.get(square);

        if (own == null || base == null || !name.startsWith(base)) return name;

        String same = own + name.substring(base.length());

        if (square.equals(squareOfNameNow.get(same))) return same;

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
     * Carries the names every configuration stores (timetable legs, standing trains' roads) to this build's Point
     * names, so a rename or a changed copy count does not make a load drop the entry: the configuration in use by its
     * build, the others across a renamed square only.  An untraceable name is left for that configuration's own build.
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

                // And the timetable legs the edit's folds took from the railway (OB-343), carried every build so they
                // name the latest Points when Cancel writes them back
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
            squareOfNameSeen.putAll(tiles);
            baseOfNameSeen.putAll(naming.baseNames());

            Map<String, Side> facings = naming.facingByName();

            facingOfNameSeen.putAll(facings);

            squareOfNameNow = tiles;
            baseOfNameNow = naming.baseNames();
            nameOfSquareNow = bases;
            facingOfNameNow = facings;
            turningNow = naming.turningNames();
        }
    }

    /** A pair of stored Point names: a timetable leg's two ends, or one step of a train's road. */
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

    /** Every stored name pair; a load drops the entry whole where one names a Point it does not build. */
    private static List<NamedLeg> legsOf(org.json.JSONObject configuration)
    {
        return new ArrayList<>(keyedLegsOf(configuration).values());
    }

    /** The same, keyed "t" + entry and leg of the timetable, or "r" + square and step of a road. */
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
     * The square an older name stands for: the longest own name it is, whole or with a heading.
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
     * Another configuration's names carried across a renamed square, in place, keeping the heading.  A name no build
     * gave is remembered by the square whose name it begins with, for a later rename.
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

                // Still built on the square it named, or on one no build of this session placed
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
     * Carries the in-use configuration's names to this build's names, in place; returns whether any changed.
     * @param edges this build's edges, as "start -> end"
     */
    private boolean carry(List<NamedLeg> legs, Map<String, TileKey> tiles, Map<TileKey, String> bases,
        java.util.Set<String> edges)
    {
        // Every name this build lacks, or gives another square, traced to its square
        Map<String, TileKey> stale = new LinkedHashMap<>();
        Map<String, String> carried = new LinkedHashMap<>();

        for (NamedLeg leg : legs)
        {
            for (String name : new String[] {leg.get(0), leg.get(1)})
            {
                if (stale.containsKey(name)) continue;

                TileKey square = squareOfNameSeen.get(name);
                String base = baseOfNameSeen.get(name);

                // Still built on the square it named, by the previous build's memory (written after this carry)
                if (tiles.containsKey(name) && (square == null || square.equals(tiles.get(name)))) continue;

                // Older than this session: the longest own name it is, whole or with a heading
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

                // The same copy under the square's name now
                String same = bases.get(square) + name.substring(base.length());

                if (square.equals(tiles.get(same))) carried.put(name, same);
            }
        }

        // That copy gone (a square split, or one copy fewer): the one copy every leg through it runs along
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

    /** What came back from a legacy autonomy.json, so the caller can say what happened. */
    public static class LegacyImport
    {

        /** Facings the import chose because the file stated none (or the square's own, no better evidence) (OB-304). */
        public int facingsInvented;

        /**
         * Points whose trains the file ran a way no copy of the square holds: stood the only allowed way, known
         * reversed (OB-270).
         */
        public final List<String> facingsNotHeld = new ArrayList<>();

        /**
         * Track the old file's one-way edges set running where the operator had set nothing, against its way now.
         * Adam: *"Carry the old file's directions onto the diagram - yes, to the extent possible."*
         */
        public final Map<TileGraph.DirectionKey, Direction> directionsCarried = new LinkedHashMap<>();

        /** Pieces of track the old file ran one way whose direction was already set here. */
        public int directionsNotCarried = 0;
        /** Names written onto a square that had none. */
        public int matched = 0;

        /** Squares left alone because they already had a name, each counted once. */
        public int skipped = 0;

        /** Old points whose square another point on the same sensor already named, as lines naming both. */
        public final List<String> namesNotKept = new ArrayList<>();

        /** Locomotives put back where the old graph had them. */
        public int placed = 0;

        /** Squares marked as turning trains round, from a terminus or a reversing point. */
        public int reversing = 0;

        /** Priorities, speed multipliers, exclusions and switches carried over. */
        public int settings = 0;

        /** Homes cleared because the locomotive already had one; only one is kept. */
        public int duplicateHomes = 0;

        /** Names whose sensor is not on this diagram, in the order the file gave them. */
        public final List<String> unmatched = new ArrayList<>();

        /** Locomotives the database does not know; not placed, as one would invalidate the WHOLE running layout. */
        public final List<String> unknownLocomotives = new ArrayList<>();

        /** The same, with the square each would have stood on (OB-349). */
        public final List<LocomotiveLeftOut> unknownPlacements = new ArrayList<>();

        /** Locomotives the file places at more than one point; only the first is placed. */
        public final List<String> duplicateLocomotives = new ArrayList<>();

        /** Locomotives already standing in the target configuration; not placed again. */
        public final List<String> alreadyPlaced = new ArrayList<>();

        /** Locomotives already homed in the target configuration; that home is kept. */
        public final List<String> homesKept = new ArrayList<>();

        /** Locomotives the file places on a square another train stands on; not placed (OB-304). */
        public final List<String> squareTaken = new ArrayList<>();

        /** Locomotives homed on a square already another train's home; not homed there. */
        public final List<String> homeSquareTaken = new ArrayList<>();

        /** Not placed because the configuration is the running one: the railway says where trains stand. */
        public final List<String> notPlacedInUse = new ArrayList<>();
    }

    /**
     * The per-point settings of an old graph that the build still reads, carried verbatim.  A station's `active: false`
     * is written as `autoDestination: false`, switch left on (Adam: *"yes, translate as on but not auto
     * destination."*).
     */
    static final List<String> CARRIED_SETTINGS =
        Arrays.asList("priority", "speedMultiplier", "excludedLocs", "active", "maxTrainLength");

    /**
     * Brings station names, flags and lengths across from a legacy autonomy.json, matching points by their s88.  Fills
     * gaps, never overwrites; writes nothing to disk (the caller saves or puts the setup back, so an import is whole).
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
     * The same, told whether the file's placements are taken (never into the running configuration).
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
     * The address in a legacy accessory name that is a signal ("Signal 116"); null for anything else or a bad number,
     * as a report should not throw on a malformed old file.
     * @param accessory the legacy command's `acc` value, which may be null
     * @return the address, or null when this is not a signal
     */
    public static Integer legacySignalAddress(String accessory)
    {
        if (accessory == null) return null;

        final String trimmed = accessory.trim();

        // The space is required, so "Signalbox" is not a signal
        if (trimmed.length() < 8 || !trimmed.substring(0, 7).equalsIgnoreCase("Signal ")) return null;

        final String rest = trimmed.substring(7).trim();

        // The digits, then whatever protocol `getNameWithProtocol` appends ("Signal 116 DCC")
        int end = 0;

        while (end < rest.length() && Character.isDigit(rest.charAt(end))) end++;

        if (end == 0) return null;

        // Digits only (`Integer.valueOf` takes "+116"); only a protocol token may follow
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
     * What a legacy import deliberately leaves behind, counted (MT-257).  Adam: **"yes, but list them in the log and
     * mention that in the dialog."**
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

        // Sorted by address: the operator works down this list with Pair Signal open.
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

                    // Signals by name: a signal is driven only where the operator pairs it.  Named, not compared, as
                    // which are paired needs the reduction; an extra name costs a glance, a missing one a lying signal.
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

                // Hand-written locks, the only silent drop: derived locks cannot join edges sharing no tile.  Counted,
                // not compared: wrong in the safe direction.
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

        // And a modern export re-imported, which `detectImportFormat` also calls legacy: reported, not refused, as a
        // key the old format never wrote is evidence, not proof.
        int modern = 0;

        if (legacy.has("points") && legacy.optJSONArray("points") != null)
        {
            org.json.JSONArray points = legacy.getJSONArray("points");

            for (int i = 0; i < points.length(); i++)
            {
                org.json.JSONObject p = points.optJSONObject(i);

                if (p == null) continue;

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
     * Brings in an exported file and derives again, so terminus, reversing and station switches follow its flags.
     * @param name what to call the configuration here
     * @param file the parsed export
     * @return how many shared entries were filled in
     */
    public int importBundle(String name, org.json.JSONObject file) throws java.io.IOException
    {
        return sessionImport.importBundle(name, file);
    }

    /** @return the pages the last import described that this layout lacks and left out (MT-380); may be empty */
    public java.util.List<String> getPagesLeftOutOfLastImport()
    {
        return store.getPagesLeftOutOfLastImport();
    }

    /**
     * Shuts any page that repeats a sensor an earlier page carries (two squares, one s88, two destinations): the
     * earliest page in layout order wins.  Run for a new configuration and for a legacy import, which may override the
     * user's pages (Adam: "yes, it may override."; OB-130); elsewhere the page checkboxes decide.
     * @return the pages this shut, in the order they appear
     */
    public List<String> excludeRepeatedSensorPages()
    {
        Set<Integer> seen = new LinkedHashSet<>();

        List<String> shut = new ArrayList<>();

        for (LayoutDiagram page : pages)
        {
            if (store.getExcludedPages().contains(page.getName())) continue;

            // A page the operator turned back on is left alone: an explicit re-enable is an opinion.
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
     * Which configuration should run after an import: whatever is already chosen, re-derived, since the import merged
     * the shared half.  Importing is not a request to switch.
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

    /** The kinds of file the one Import action accepts. */
    public static enum ImportFormat
    {
        /** A configuration and the track decisions it refers to, as exportBundle writes them. */
        BUNDLE,

        /** A configuration on its own, as exporting wrote it before bundles existed. */
        CONFIGURATION,

        /** An autonomy.json from the graph this feature replaces. */
        LEGACY_GRAPH,

        /** Something else entirely: a routes file, a locomotive database, a diagram. */
        UNKNOWN
    }

    /**
     * Which kind a parsed file is: BUNDLE has "configuration"; LEGACY_GRAPH has "points" as an ARRAY; CONFIGURATION has
     * "points" as an OBJECT keyed by square.
     * @param file the parsed file
     * @return what it is
     */
    public static ImportFormat detectImportFormat(org.json.JSONObject file)
    {
        return SessionImport.detectImportFormat(file);
    }

    /**
     * Each copy of a square a train may be put down on (one trains may arrive at), with its facing (OB-270).  The
     * placement doors use `departableFacingsFor` instead.  With no running layout every copy is returned.
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
     * Each copy of a square a train standing on it could leave, with its facing (OB-284).  Adam: *"this should check
     * for barred departure directions, not arrival ones.  for barred arrival directions, keep the direction.  for
     * barred departure directions, turn it to a way trains may arrive in"*.  With no running layout every copy is
     * returned.
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
     * The copy a train facing this way stands on: one trains may arrive at, not turning it, where there is one
     * (MT-368); else any copy facing that way, since the copy IS the direction (autonomy then refuses to start it,
     * saying why).
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

        // None a train may arrive at faces that way: any that does, the plain one first.
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
     * The copy a train stands on when nothing says its facing: the build's `startableCopy`; no facing is recorded
     * (OB-350).
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

    /** The way one named locomotive faces on a square, read off the copy the running layout has it on, or null. */
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
     * Stands a locomotive on the copy facing a given way.  Not `moveLocomotive`, which refuses while running, needs a
     * destination and adds to the run list.  Quiet about whatever it cannot do.
     */
    private void moveOntoFacingCopy(org.traincontrol.automation.Layout running, String locomotive,
        TileKey tile, Side facing)
    {
        if (running == null || locomotive == null || facing == null) return;

        org.traincontrol.base.Locomotive train = null;

        // Never a copy facing another way, even where only a barred copy faces this one (`copyFacing`)
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

        if (onto == null || train == null) return;

        // Not a train under way: its locked path reads as standing on every square, and a move would sweep it off.
        if (running.isAlreadyUnderway(train)) return;

        if (onto.getCurrentLocomotive() == train) return;

        // Already on an arrivable copy facing that way: left, or a train turned on a turning copy would lose its
        // timetable place (as `Layout.copyAfterTheTurn`)
        if (from != null && facing == facingsFor(tile).get(from.getName()) && from.isDestination()) return;

        // And the tail comes with it, which `setLocomotive` clears (behaviour.md 4: turning round at the platform does
        // not move the tail); read from the copy it was on.
        final String tail = from == null ? null : from.getArrivedFrom();

        // And the route it drove, which it follows back past a junction (MT-335)
        final java.util.List<org.traincontrol.automation.Edge> along =
            from == null ? null : from.getArrivedAlong();

        // Cleared first, so the train is never on two copies at once.
        for (org.traincontrol.automation.Point point : here)
        {
            if (point.getCurrentLocomotive() == train) point.setLocomotive(null);
        }

        onto.setLocomotive(train);

        if (tail != null) onto.setArrivedFrom(tail);

        if (along != null) onto.setArrivedAlong(along);

        // And the railway's watchers told (MT-759): the diagram redraws a standing train only then.
        running.refreshUI();
    }

    /**
     * Faces a train the way it came in, because the railway turned it round there: absolute, unlike `flipFacing`, whose
     * relative answer is untrustworthy when `reconcileFacingWhenIdle` runs (OB-190), so applying it twice is harmless.
     * Writes both the setup and the running layout.  Null where it cannot answer; the caller retries next refresh.
     * @param locomotive the locomotive that was turned
     * @param turnedAt the Point it was standing on when it turned, from the record the arrival made
     * @param running the running layout, or null to write the setup only
     * @return the square whose facing was written, or null when nothing was
     */
    public TileKey faceTheWayItCameIn(String locomotive, org.traincontrol.automation.Point turnedAt,
        org.traincontrol.automation.Layout running)
    {
        if (locomotive == null || turnedAt == null || getStationIndex() == null) return null;

        // Still standing where it turned: any newer placement, paste or rebuild is somebody's newer answer.
        if (turnedAt.getCurrentLocomotive() == null
            || !locomotive.equals(turnedAt.getCurrentLocomotive().getName()))
        {
            return null;
        }

        // And not under way again, or the move would sweep it off its locked path.
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

        // A side no copy can face (OB-177): the build cannot hold it.
        if (!facingChoices(tile).contains(arrived)) return null;

        setFacing(tile, arrived);

        moveOntoFacingCopy(running, locomotive, tile, arrived);

        return tile;
    }
    /**
     * Turns a placed locomotive round on the graph because the railway turned it.  Adam: **"changing the direction on
     * the graph itself does not emit a locomotive direction command.  But a locomotive direction command WILL update
     * the direction on the graph if it does not match."**  Flipping is "the other choice" of `facingChoices`, not the
     * compass opposite.  Null where the answer is not obvious.  Also updates the running layout (Adam: *"flipFacing
     * should also update the running layout."*), whose copy decides where the train can go.
     * @param locomotive the locomotive that has just been turned
     * @param running the layout to move it on, or null to write the setup only
     * @return the square whose facing changed, or null when nothing did
     */
    public TileKey flipFacing(String locomotive, org.traincontrol.automation.Layout running)
    {
        if (locomotive == null) return null;

        // The railway first: a reversal during a run is applied at its end, when the setup still names the start.  The
        // setup is still walked in full, so a train recorded on two squares is flipped on the decidable one.
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

        // The setup only when the railway does not know, or the platform the train left would be flipped.
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
            // Its facing on the railway (the copy IS its facing); the setup's answers only where the railway does not.
            Side recorded = facingOfTrainOnTheRailway(tile, locomotive, running);

            if (recorded == null) recorded = getFacing(tile);

            List<Side> choices = facingChoices(tile);

            // Carry on: a train recorded on two squares still gets the decidable one.
            if (recorded == null || choices.size() != 2) continue;

            // A facing this square cannot hold (OB-177): reported by `facingsThatCannotBeHeld`, not flipped.
            if (!choices.contains(recorded)) continue;

            final Side now = choices.get(0) == recorded ? choices.get(1) : choices.get(0);

            setFacing(tile, now);

            moveOntoFacingCopy(running, locomotive, tile, now);

            return tile;
        }

        return null;
    }
    /** The locomotive on a stored point, or null: the one spelling of what a blank name means. */
    private static String namedLocomotiveOn(org.json.JSONObject point)
    {
        org.json.JSONObject standing = point == null ? null : point.optJSONObject("loc");

        if (standing == null) return null;

        String name = standing.optString("name", "");

        return name.trim().isEmpty() ? null : name;
    }

    /** A square's stored point in the active configuration, or null. */
    private org.json.JSONObject pointOf(TileKey tile)
    {
        String active = store.getActiveConfiguration();

        org.json.JSONObject configuration = active == null ? null : store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return null;

        return configuration.getJSONObject("points").optJSONObject(tile.toString());
    }
    /**
     * Which locomotive the active configuration records on each square (what the next build reads).  Excluded pages
     * are left out: the duplicate finding this feeds is an ERROR and must not send the user to one.
     */
    private Map<TileKey, String> placedLocomotives()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();

        // Excluded pages are filtered here, not in the shared walk: it belongs to this question
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

    /** What a caption looked like in the layout file; only the one-time `migrateStationLabels` reads it. */
    public static final String STATION_LABEL_PREFIX = "Point:";

    /**
     * Every station some square of some page shows the name of, excluded pages included (exclusion stops routing, not
     * drawing).  Stations only: a caption kept for a non-station labels nothing.
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
     * The station a caption on this square is about; none when that square is not a station now.  A demoted station's
     * caption is kept but not drawn, for the moves and deletes that must carry or forget it.
     * @param captionTile the square the text sits on
     * @return the sensor's square, or null when nothing is captioned there or the sensor is not a station now
     */
    public TileKey getCaptionTarget(TileKey captionTile)
    {
        TileKey station = store.getCaptionTarget(captionTile);

        return station != null && store.isStation(station) ? station : null;
    }

    /** @return every caption square of this station, drawn or kept (`getCaptionTarget` says which); possibly none */
    public Set<TileKey> captionsFor(TileKey stationTile)
    {
        return store.captionsFor(stationTile);
    }

    /** Every caption on the layout, as the square it is drawn on to the sensor it is about. */
    public Map<TileKey, TileKey> getCaptions()
    {
        return store.getCaptions();
    }

    /**
     * Whether the diagram draws the caption on this square: the one rule for drawing and the setup check (MT-337).
     * Once PER PAGE (Adam: *"The duplicate caption check should be per page.  It is OK to show on another page what is
     * going on on another."*): one on the station's own square gives way to one beside it on the same page.  Not on a
     * page that hides captions, and not for an unnamed station switched off (Adam: *"hide the label if empty and
     * nothing can pass."*).
     * @param where the square the text sits on
     * @return whether a caption is drawn there
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

    private boolean isUnnamedAndSwitchedOff(TileKey station)
    {
        if (!Boolean.FALSE.equals(getPointProperty(station, "active"))) return false;

        String named = store.getPointName(station);

        return named == null || named.trim().isEmpty();
    }

    /**
     * Whether the running diagram draws this square, where a caption can be seen: only what its components cover (a
     * caption is not one).  False for a page this session does not have.
     */
    public boolean isInTheDrawnArea(TileKey square)
    {
        LayoutDiagram page = pageOf(square);

        return page != null && page.isDrawnWhenRunning(square.getX(), square.getY());
    }

    /** Whether the square's page is one this session has and autonomy uses. */
    private boolean onAPageThatDrawsCaptions(TileKey square)
    {
        // Stale page objects cannot say a page is gone (`pagesStale`)
        return (pagesStale || pageOf(square) != null) && !store.getExcludedPages().contains(square.getPage());
    }

    /** The squares the diagram draws a station's name on (`isCaptionDrawn`). */
    public List<TileKey> drawnCaptionsFor(TileKey station)
    {
        List<TileKey> drawn = new ArrayList<>();

        for (TileKey where : captionsFor(station))
        {
            if (isCaptionDrawn(where)) drawn.add(where);
        }

        return drawn;
    }

    /** Shows a station's name on a square (`stationTile` null clears the square). */
    public void setCaption(TileKey captionTile, TileKey stationTile)
    {
        // One caption per station PER PAGE, decided here for every door: moved on the new square's page, added on a
        // page with none, kept on others.  Cleared first, so re-setting the same square is not a clear-then-set.
        if (stationTile != null) clearCaptions(stationTile, captionTile);

        store.setCaption(captionTile, stationTile);
        touched();
    }

    /**
     * Forgets the captions of a square deleted from the diagram: the one on it (an emptied square keeps its caption)
     * and every one naming it.
     * @param tile the square being deleted
     * @return true when something was forgotten
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

    /** Everything the setup holds, for a caller that may have to put it all back (`restoreSetup`). */
    public org.json.JSONObject snapshotSetup()
    {
        return store.snapshotSetup();
    }

    /**
     * Notes the setup now, so an editing session that dies can be undone (OB-108).
     * @return whether the note reached the disk
     */
    public boolean beginEditSession()
    {
        // Not the names a last edit's Cancel kept (`restoreSetup`)
        forgetTheNamesKept();

        asTheEditBegan = remembered();

        // And what the railway changes alone from here, which Cancel keeps (OB-343)
        takenFromTheRailway = new LinkedHashMap<>();

        pausesTakenFromTheRailway = new LinkedHashMap<>();

        return store.rememberBeforeEdit(store.snapshotSetup());
    }

    /** The editing session ended properly, so there is nothing to undo. */
    public boolean endEditSession()
    {
        takenFromTheRailway = null;

        pausesTakenFromTheRailway = null;

        forgetTheNamesKept();

        return store.forgetBeforeEdit();
    }

    /**
     * What the settings folds took from the railway during an edit session, by configuration (JSON null where the
     * railway stopped writing one); null while none is open.  The railway's facts, not the edit's, so Cancel keeps them
     * (OB-343).
     */
    private java.util.Map<String, org.json.JSONObject> takenFromTheRailway;

    /**
     * The same for paused trains, by train: each one the railway's list gained or lost against the record as built
     * (OB-343).  By name, followed through renames (`followALocomotiveName`).
     */
    private java.util.Map<String, java.util.Map<String, Boolean>> pausesTakenFromTheRailway;

    /**
     * Follows a locomotive's rename or deletion (`to` null) into the trains the folds noted (OB-343), or it would come
     * out paused after Cancel.
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

    /** Forgets the names of an edit's last build that Cancel kept (`keptSquares`). */
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

    /** Writes what the folds took from the railway since the edit began over the setup just put back (OB-343). */
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

    /** A pre-edit note of the right shape that still would not read (`unusableEditNote`). */
    private boolean editNoteWouldNotRead;

    /**
     * Whether a pre-edit note on disk could not be used; asked after `revertUnfinishedEdit` returns false.
     * @return true when a note is there and was refused
     */
    public boolean unusableEditNote()
    {
        return store.hasUnfinishedEditNote() && (editNoteWouldNotRead || store.unfinishedEdit() == null);
    }

    /**
     * Puts the setup back to before an editing session that never ended (the process died with the editor open).
     * Called whenever a session is built; never while an editor is open (`TrainControlUI.getAutonomySession`).
     * @return true when something was put back
     */
    public boolean revertUnfinishedEdit()
    {
        org.json.JSONObject was = store.unfinishedEdit();

        if (was == null) return false;

        // A note that will not read inside (a wrong-typed value) is refused and kept, like one of the wrong shape:
        // letting it throw would leave autonomy unopenable at every start.
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

    /** Puts back a whole `snapshotSetup` and writes it out; returns whether it reached the file. */
    public boolean restoreSetup(org.json.JSONObject was)
    {
        if (was == null) return true;

        store.restoreSetup(was);

        // What the railway changed alone during the edit stays changed (OB-343)
        putBackWhatTheRailwayChangedAlone();

        // And the carry's memory and the railway's squares as before the first move.  But the names the edit's LAST
        // build gave are kept until the session ends (`keptSquares`, OB-343): the railway's timetable names them, and
        // forgetting them would make the next load drop those legs.
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

    /** Everything the setup holds about one page, for the diagram editor's undo (restorePage). */
    public java.util.Map<String, Object> snapshotPage(String page)
    {
        java.util.Map<String, Object> snapshot = store.snapshotPage(page);

        // And what an edit of the page changes outside it (`Remembered`)
        snapshot.put(REMEMBERED, remembered());

        return snapshot;
    }

    /** Puts a page's setup back as it was, and rebuilds the graph from it. */
    public void restorePage(String page, java.util.Map<String, Object> snapshot)
    {
        if (snapshot == null) return;

        store.restorePage(page, snapshot);

        Object remembered = snapshot.get(REMEMBERED);

        if (remembered instanceof Remembered) putBack((Remembered) remembered, true);

        touched();
    }

    /**
     * Writes the setup out without reconciling it, for the diagram editor: its edits must reach disk as they happen,
     * and a reconcile would delete everything on the half of the diagram not yet caught up.
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
     * Moves everything written about these squares with them, or the next reconcile drops it; the whole group at once
     * (`AutonomyCompanionStore.moveTiles` says why).
     * @param moves each square being vacated, and where it is going
     * @return true when anything moved
     */
    public boolean moveTiles(Map<TileKey, TileKey> moves)
    {
        return moveTiles(moves, null);
    }

    /**
     * One diagram edit: what moved, and what was built over.
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

        // Whether the store changed: callers read "something was forgotten, so write the setup"
        boolean changed = store.moveTiles(moves, builtOver);

        // No fold of a railway built before the move (`squaresMoved`)
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

        if (builtOver != null && !builtOver.isEmpty()) squaresMoved = true;

        // The railway's squares and the carry's memory move with the setup's (`Remembered`)
        if (moved)
        {
            if (asTheEditBegan == null) asTheEditBegan = remembered();

            moveTheRailwaysSquares(square -> moves.get(square) != null ? moves.get(square) : square);

        // A name made from a moved square ("page x,y") is the store's to rename, not the memory's to carry
            squareOfNameSeen.entrySet().removeIf(seen -> moves.get(seen.getValue()) != null
                && GraphReducer.generatedName(seen.getValue()).equals(baseOfNameSeen.get(seen.getKey())));

            for (Map.Entry<String, TileKey> seen : squareOfNameSeen.entrySet())
            {
                TileKey to = moves.get(seen.getValue());

                if (to != null) seen.setValue(to);
            }
        }

        // Unconditional: building over a blank square changes the graph though the store held nothing; writing is the
        // caller's call.  A square built over takes its run's direction silently (OB-361).
        joiningTheirRuns = builtOver == null ? Collections.<TileKey>emptySet() : new LinkedHashSet<>(builtOver);

        try
        {
            touched();
        }
        finally
        {
            joiningTheirRuns = Collections.emptySet();
        }

        // And what the rebuild gave them, or the next open would log the run as a correction
        return changed || !graph.getNormalizedRuns().isEmpty() || !graph.getNormalizedToArms().isEmpty();
    }

    /** Squares just built over, which take their runs' direction silently (OB-361). */
    private Set<TileKey> joiningTheirRuns = Collections.emptySet();

    /**
     * Squares whose track was replaced: what was written described the old tile, and reconcile only clears EMPTY ones.
     * @param tiles the squares built over
     * @return true when there was anything to forget
     */
    public boolean forgetTiles(java.util.Collection<TileKey> tiles)
    {
        return moveTiles(null, tiles);
    }

    /** One tile, for the single-tile drag. */
    public boolean moveTile(TileKey from, TileKey to)
    {
        Map<TileKey, TileKey> one = new LinkedHashMap<>();

        one.put(from, to);

        return moveTiles(one);
    }

    /**
     * Moves a station's caption with a dragged tile.
     * @param from the square being vacated
     * @param to where it is going
     * @return true when a caption actually moved
     */
    public boolean moveCaption(TileKey from, TileKey to)
    {
        if (from == null || to == null || from.equals(to)) return false;

        TileKey station = store.getCaptionTarget(from);

        if (station == null) return false;

        store.setCaption(from, null);

        // Through setCaption: one per station per page
        setCaption(to, station);

        return true;
    }

    /** The page a key names, or null if this session has never heard of it. */
    private LayoutDiagram pageOf(TileKey tile)
    {
        for (LayoutDiagram page : pages)
        {
            if (page.getName().equals(tile.getPage())) return page;
        }

        return null;
    }

    /**
     * Puts an uncaptioned station's name where it is readable: plain track beside it, else an empty square beside it,
     * else the sensor; never over somebody's caption.  With nowhere to put it the "not shown anywhere" warning stands.
     * @param tile the station
     * @return what happened, so a caller can say why nothing did
     */
    public String placeCaption(TileKey tile)
    {
        // The authored name: a new station has only the reducer's coordinate name, worse than no caption.
        String name = store.getPointName(tile);

        if (name == null || name.trim().isEmpty()) return "autosetup.ui.labelNotNamedYet";

        // Already shown on this page: MOVED, the old one cleared only once a new place is found; other pages' stay
        // (OB-341).

        LayoutDiagram page = pageOf(tile);

        if (page == null || graph == null) return "autosetup.ui.labelNoDiagram";

        LayoutDiagramComponent here = page.getComponent(tile.getX(), tile.getY());

        if (here == null) return "autosetup.ui.labelNoDiagram";

        // Below for a station lying north-south, left for one lying east-west, then the other three
        List<Side> sides = labelSides(tile);

        for (Side side : sides)
        {
            TileKey at = neighbour(tile, side);

            if (!connects(tile, side, at)) continue;

            LayoutDiagramComponent next = page.getComponent(at.getX(), at.getY());

            if (next == null || next.hasLabel() || next.isFeedback()) continue;

            if (store.getCaptionTarget(at) != null) continue;

            // Straight through, not of type STRAIGHT: a signal or uncoupler is plain line with a fitting.
            if (!runsStraightThrough(next)) continue;

            setCaption(at, tile);

            return null;
        }

        // Then an empty square beside it; nothing is added to the diagram.
        for (Side side : sides)
        {
            TileKey at = neighbour(tile, side);

            // Inside what the running diagram draws: getComponent is null off the edge as on a blank square.
            if (!page.isDrawnWhenRunning(at.getX(), at.getY())) continue;

            if (page.getComponent(at.getX(), at.getY()) != null) continue;

            if (store.getCaptionTarget(at) != null) continue;

            setCaption(at, tile);

            return null;
        }

        // Last resort: the sensor itself.
        if (!here.hasLabel())
        {
            setCaption(tile, tile);

            return null;
        }

        return "autosetup.ui.labelNoRoom";
    }

    /**
     * Forgets this station's captions on the page of `except`, keeping other pages'.  Called only once somewhere new is
     * found, or a failed placement would remove the name that was there.
     */
    private void clearCaptions(TileKey station, TileKey except)
    {
        for (TileKey where : new LinkedHashSet<>(captionsFor(station)))
        {
            if (where.equals(except) || !where.getPage().equals(except.getPage())) continue;

            store.setCaption(where, null);
        }
    }

    boolean isCaptionedOnPage(TileKey station, String page)
    {
        for (TileKey where : captionsFor(station))
        {
            if (where.getPage().equals(page)) return true;
        }

        return false;
    }

    /** Whether leaving this square by that side lands on that one: connected, not merely adjacent. */
    private boolean connects(TileKey tile, Side side, TileKey to)
    {
        if (graph == null) return false;

        TileGraph.Landing landing = graph.landing(tile, side);

        return landing != null && to.equals(landing.getTile());
    }

    private boolean runsStraightThrough(LayoutDiagramComponent component)
    {
        List<Route> routes = TilePorts.ports(component.getType(),
            component.getOrientation(), 0);

        if (routes.size() != 1) return false;

        Route route = routes.get(0);

        return route.getA() != null && route.getB() != null
            && route.getA() == route.getB().opposite();
    }

    /** The sides to try, best first: below for a station lying north-south, left for one lying east-west. */
    private List<Side> labelSides(TileKey tile)
    {
        boolean vertical = false;

        // Asked of the graph, not ports(): a triggered sensor reports state 1, past its one state, and would show no
        // track.
        for (Route route : graph.getRoutes(tile).values())
        {
            if (route.touches(Side.N) && route.touches(Side.S)) vertical = true;
        }

        return vertical
            ? java.util.Arrays.asList(Side.S, Side.N, Side.W, Side.E)
            : java.util.Arrays.asList(Side.W, Side.E, Side.S, Side.N);
    }

    /** The square on the given side of this one; north is the smaller y. */
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
     * Brings "Point:<name>" labels in the diagram across into the setup as captions keyed by square, once (a name broke
     * on renames and splits); a label naming nothing is dropped.  The setup is written before the pages, so a failed
     * page write leaves both and the migration runs again.
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
                    // One caption per station per page (OB-341): the setup's, chosen by somebody, stays; the label
                    // still leaves the page, or it would be found at every open.
                    if (!isCaptionedOnPage(station, where.getPage())) store.setCaption(where, station);

                    migrated = true;

                    // Counted per page and totalled only once the page is written: a page whose write threw is in
                    // neither.
                    Integer soFar = takenFrom.get(entry.getKey());

                    takenFrom.put(entry.getKey(), soFar == null ? 1 : soFar + 1);
                }
            }
        }

        List<String> failures = new ArrayList<>();

        // Nothing to write: saving would create a setup for a layout with no autonomy and rewrite pages at every
        // launch.
        if (!migrated) return failures;

        try
        {
            store.save();
        }
        catch (IOException e)
        {
            failures.add(String.valueOf(e.getMessage()));

            return failures;
        }

        for (Map.Entry<LayoutDiagram, List<LayoutDiagramComponent>> entry : found.entrySet())
        {
            boolean changed = false;

            for (LayoutDiagramComponent component : entry.getValue())
            {
                // Only labels that became a caption: dropping orphans meant not drawing them, not editing the diagram.
                String was = component.getLabel();

                if (was == null || !was.startsWith(STATION_LABEL_PREFIX)) continue;

                if (tileNamed(was.substring(STATION_LABEL_PREFIX.length())) == null) continue;

                // Emptied: the exporter writes no TEXT element without text.
                component.setLabel("");

                changed = true;
            }

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

    /** The square of the station carrying this authored name, or null if no station does. */
    private TileKey tileNamed(String name)
    {
        if (name == null) return null;

        // Every named square, not only the reduction's (built without excluded pages, and excluding is reversible).
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
     * The per-point keys a configuration owns.  Terminus and reversing are absent: both are derived at build, and
     * capturing one would make a may-turn square reverse every train.
     */
    private static final List<String> POINT_OPERATIONAL_KEYS = java.util.Arrays.asList(
        "loc", "active", "maxTrainLength", "speedMultiplier",
        "priority", "home", "excludedLocs",
        // with its mode (FR-121), or a capture writes the list back read the other way
        "excludedLocsMode",
        // Where a standing train's tail lies, which a run writes only on the running layout
        "arrivedFrom", "arrivedAlong");

    /** The keys that say where a train stands: the train and its tail's side and road. */
    private static final List<String> PLACEMENT_KEYS = java.util.Arrays.asList("loc", "arrivedFrom", "arrivedAlong");

    /**
     * Squares where trains turn round whose stretch back to the last switch is not fully measured, one entry each
     * (OB-171): `Layout.isPathClear` cannot refuse a reversal into a berth it cannot measure.  Adam: "Add notices to
     * the autonomy editor to add track lengths between stations and switches that accept reversal (if any other track
     * length is set anywhere)."  The stretch is walked as `GraphReducer.roomAfterTheLastSwitch` walks it.
     * @return each square mapped to how many squares its guard still needs, empty when the layout measures nothing
     */
    public java.util.Map<TileKey, Integer> reversalsWithoutLength()
    {
        java.util.Map<TileKey, Integer> out = new java.util.LinkedHashMap<>();

        if (store == null || reducer == null) return out;

        // Asked of the store: the plain track between sensors carries a length but no point.
        if (!store.measuresAnyTrack()) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (!isTurnAround(tile)) continue;

            java.util.Set<TileKey> missing = new java.util.LinkedHashSet<>();

            // The reversal square is part of the stretch (MT-364); asked alone only where no edge arrives.
            boolean anApproachExists = false;

            // And the track behind it after the last switch.  A partly measured stretch still reports: the guard is
            // then pessimistic, the safe way, and still worth telling (MT-305).
            for (GraphReducer.ReducedEdge arriving : reducer.getEdges())
            {
                if (!arriving.getEnd().equals(tile)) continue;

                // A side no train stops from is still asked about: a barred side keeps its turning copy, and the room
                // rule judges that turn over this stretch.
                anApproachExists = true;

                // An answered 0 is measured track of no length, not missing.
                for (TileKey unmeasured : reducer.unmeasuredAfterTheLastSwitch(arriving))
                {
                    if (!store.isTileLengthAnswered(unmeasured)) missing.add(unmeasured);
                }
            }

            if (!anApproachExists && store.getTileLength(tile) <= 0 && !store.isTileLengthAnswered(tile))
            {
                missing.add(tile);
            }

            if (!missing.isEmpty()) out.put(tile, missing.size());
        }

        return out;
    }

    /**
     * One piece of track to measure for Mass Assign Lengths: the squares of a leg between two fixed points (sensor or
     * switch), switches excluded, so an even share never lands across a switch.  A sensor square ending several legs
     * belongs to the first, so no answer overwrites another.
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

        /** @return the squares, in the order the leg runs */
        public java.util.List<TileKey> getTiles()
        {
            return tiles;
        }

        /** @return the fixed point at one end (a sensor square, or the switch the piece stops short of) */
        public TileKey getFrom()
        {
            return from;
        }

        /** @return the fixed point at the other end */
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
     * Every piece of track a length rule reads, on the pages autonomy uses: every leg, cut at its switches.  A tool for
     * measuring everything, unlike the quieter `reversalsWithoutLength` notice (MT-305).
     * @return the pieces, ordered by page and position
     */
    public java.util.List<Stretch> stretchesALengthRuleReads()
    {
        java.util.List<Stretch> out = new java.util.ArrayList<>();

        if (store == null || reducer == null || getGraph() == null) return out;

        java.util.Set<TileKey> placed = new java.util.HashSet<>();

        // A square two legs run over is cut out, as a switch is.
        java.util.Set<TileKey> shared = sharedSquaresALengthRuleReads();

        for (java.util.List<TileKey> leg : legsOnce())
        {
            java.util.List<TileKey> piece = new java.util.ArrayList<>();
            int firstIndex = -1;

            for (int i = 0; i <= leg.size(); i++)
            {
                // A route tile is in no piece and cuts none (OB-273): the piece runs on across it.
                if (i < leg.size() && takesNoLength(leg.get(i))) continue;

                boolean boundary = i == leg.size()
                    || isSwitchSquare(leg.get(i)) || shared.contains(leg.get(i));

                if (!boundary)
                {
                    if (firstIndex < 0) firstIndex = i;

                    // The first piece that reaches a square keeps it, so no answer is overwritten.
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
     * Every switch square on a leg autonomy uses; asked for on their own, one turnout length a page (Adam: "One length
     * for all switches").
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
     * The pieces still needing a length: whose squares add up to nothing (Adam: *"It is only indeterminate if the
     * entire logical segment has length 0."*).  The store keeps 0 and "not given" alike.
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

    /** The pieces needing a length with a square on this page, which Mass Assign Lengths walks. */
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

    /** The switch squares on this page with no length, which Mass Assign Lengths asks for together. */
    public java.util.Set<TileKey> switchesNeedingALengthOn(String page)
    {
        java.util.Set<TileKey> out = new java.util.LinkedHashSet<>();

        for (TileKey tile : switchesALengthRuleReads())
        {
            if (tile.getPage() != null && tile.getPage().equals(page) && squareNeedsALength(tile)) out.add(tile);
        }

        return out;
    }

    /** Every square Unmeasured Track highlights: pieces adding up to nothing and switches with no length. */
    public java.util.Set<TileKey> squaresNeedingALength()
    {
        java.util.Set<TileKey> out = new java.util.LinkedHashSet<>();

        for (Stretch stretch : stretchesNeedingALength()) out.addAll(stretch.getTiles());

        for (TileKey tile : switchesALengthRuleReads())
        {
            if (squareNeedsALength(tile)) out.add(tile);
        }

        // And squares two roads share, which are in no piece
        for (TileKey tile : sharedSquaresALengthRuleReads())
        {
            if (squareNeedsALength(tile)) out.add(tile);
        }

        return out;
    }

    /**
     * The least whole length a piece can be given: 0, a deliberate answer (OB-274).
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
     * Shares a piece's length evenly over its squares; only a piece adding up to nothing is written.  A leftover unit
     * goes first to a square a train stands on, as Adam measures by hand (OB-278); the rules read only the total.
     * @param stretch the piece
     * @param wholeLength its whole length, at least 1
     * @return whether it was written
     */
    public boolean assignStretchLength(Stretch stretch, int wholeLength)
    {
        if (stretch == null || wholeLength < 0 || measuredIn(stretch) > 0) return false;

        // A deliberate 0 is an answer (OB-274): measured track of no length to every rule but the own-tail one
        // (OB-300).
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
     * @return whether it was written
     */
    public boolean assignSwitchLength(java.util.Collection<TileKey> switches, int length)
    {
        if (switches == null || switches.isEmpty() || length < 0) return false;

        boolean wrote = false;

        for (TileKey tile : switches)
        {
            if (store.getTileLength(tile) > 0) continue;

            // 0 answers it (OB-274): two switches back to back are adjacent tracks
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
     * This page's stations with no maximum train length, row by row, for Mass Assign Station Max Train Lengths.  Adam:
     * *"add a similar feature to walk stations that don't have a max length set up, so I can enter it"*.  Asks
     * `hasNoMaximumTrainLength`, as the notice does, without the notice's gate.
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
     * Gives a station its maximum train length if it has none yet (one set meanwhile is kept).  0 is refused: to the
     * railway 0 is "any length".
     * @return whether it was written
     */
    public boolean assignMaxTrainLength(TileKey station, int length)
    {
        if (station == null || length < 1 || !hasNoMaximumTrainLength(station)) return false;

        setPointProperty(station, "maxTrainLength", length);

        return true;
    }

    /** Whether this square is a running-graph station taking a train of any length (no maximum, or 0). */
    private boolean hasNoMaximumTrainLength(TileKey square)
    {
        if (reducer == null || store == null || !reducer.getPoints().containsKey(square) || !store.isStation(square))
        {
            return false;
        }

        Object value = getPointProperty(square, "maxTrainLength");

        return !(value instanceof Number) || ((Number) value).intValue() <= 0;
    }

    /** Every leg once, in the direction whose key sorts first, so the answer does not depend on edge order. */
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
     * Squares more than one leg runs OVER (crossings, double curves with track on both roads), cut out of every piece
     * and asked for alone, or a piece's shared length would count on the other road too.  Adam: *"For crossings: if its
     * length is set, count that length once in each direction."*  Sensor squares and switches are not listed.
     * @return the squares, ordered by page, row then column
     */
    public java.util.Set<TileKey> sharedSquaresALengthRuleReads()
    {
        java.util.List<TileKey> out = new java.util.ArrayList<>();

        if (store == null || reducer == null || getGraph() == null) return new java.util.LinkedHashSet<>(out);

        java.util.Map<TileKey, Integer> crossedBy = new java.util.LinkedHashMap<>();

        for (java.util.List<TileKey> leg : legsOnce())
        {
            // Occurrences, not legs: a figure of eight runs one leg over its own crossing twice.
            for (int i = 1; i + 1 < leg.size(); i++) crossedBy.merge(leg.get(i), 1, Integer::sum);
        }

        for (java.util.Map.Entry<TileKey, Integer> entry : crossedBy.entrySet())
        {
            // Not a route tile: it takes no length (OB-273).
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

    /** The shared squares on this page with no length, which Mass Assign Lengths asks for together. */
    public java.util.Set<TileKey> sharedSquaresNeedingALengthOn(String page)
    {
        java.util.Set<TileKey> out = new java.util.LinkedHashSet<>();

        for (TileKey tile : sharedSquaresALengthRuleReads())
        {
            if (tile.getPage() != null && tile.getPage().equals(page) && squareNeedsALength(tile)) out.add(tile);
        }

        return out;
    }

    private boolean takesNoLength(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagramComponent component = getGraph().getTiles().get(tile);

        return component != null && TilePorts.takesNoLength(component.getType());
    }

    /**
     * Whether a length rule's piece is cut here, as `GraphReducer.boundsTheRoom` cuts the room walk: a switch or a
     * permanent turnout (OB-233), but not a square of a two-square crossing (OB-320).
     */
    private boolean isSwitchSquare(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagramComponent component = getGraph().getTiles().get(tile);

        if (component == null || getGraph().crossingPartner(tile) != null) return false;

        return component.isSwitch() || TileGraph.isPermanentTurnout(component.getType());
    }

    /**
     * Whether a berth's room ends here: a switch or permanent turnout (OB-233), or a crossing another road runs over.
     * Errs to warning where that road is unreachable.
     */
    private boolean endsTheBerthsRoom(TileKey tile, TileKey berth)
    {
        org.traincontrol.base.LayoutDiagramComponent component = getGraph().getTiles().get(tile);

        if (component == null) return false;

        // A two-square crossing is a crossing (OB-320), not two turnouts
        if (getGraph().crossingPartner(tile) != null) return anotherRoadRunsOver(tile, berth);

        if (component.isSwitch() || TileGraph.isPermanentTurnout(component.getType())) return true;

        return component.getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.CROSSING
            && anotherRoadRunsOver(tile, berth);
    }

    /**
     * Whether a road neither starting nor ending at this berth runs over this square: `Layout.whyABerthCannotHoldIt`'s
     * own question.
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

    /** By page, then row, then column. */
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
     * Whether a piece still needs asking: nothing measured, not answered 0 (OB-274).  A square added later has no
     * answer, so the piece is asked again.
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

    /** Whether a switch or shared square still needs asking: neither measured nor answered 0 (OB-274). */
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
     * Squares with an arrival copy a train can be sent to and never leave, read off the graph as built (square-level
     * checks pass while one copy is stuck).  Adam: "We need a warning for instances like the previous version of this."
     * @return the squares to warn about, mapped to the copy that is at fault
     */
    public java.util.Map<TileKey, String> destinationCopiesWithNoWayOut()
    {
        return badCopies(true, builtForInspection(), null);
    }

    /**
     * The same, over a graph somebody has already built.
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
     * The same for a copy nothing can reach: a train left there by hand is one Return Home never moves, so on a through
     * route it blocks everything behind it.
     * @return the squares to warn about, mapped to the copy that is at fault
     */
    public java.util.Map<TileKey, String> destinationCopiesWithNoWayIn()
    {
        return badCopies(false, builtForInspection(), null);
    }

    /**
     * The same, over a graph somebody has already built.
     * @param built the configuration as JSON, or null to build one here
     * @param byName the builder's own tile-name map, or null to ask for one here
     * @return the squares to warn about, mapped to the copy that is at fault
     */
    java.util.Map<TileKey, String> destinationCopiesWithNoWayIn(org.json.JSONObject built,
        java.util.Map<String, TileKey> byName)
    {
        return badCopies(false, built, byName);
    }

    /** Reads the built configuration for destination copies with no way out (`wantNoWayOut`) or none in. */
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

        // The builder's own mapping: a copy name taken apart finds nothing on an unnamed square.
        java.util.Map<String, TileKey> byName = named != null ? named : builder(null).tilesByName();

        java.util.Set<String> good = wantNoWayOut ? hasOut : hasIn;

        for (String copy : stations)
        {
            if (good.contains(copy)) continue;

            TileKey tile = byName.get(copy);

            if (tile == null || out.containsKey(tile)) continue;

            // Only where the square is otherwise healthy: an all-stuck square is the square-level checks' to report.
            if (!hasAHealthySibling(copy, tile, good, stations, byName)) continue;

            out.put(tile, copy);
        }

        return out;
    }

    /**
     * Stations whose arrival copy has no outgoing edge, for arrivals a train can make (OB-361), each with the sides
     * those arrivals come in by (OB-201).  Adam: *"MainLine isn't a station, and it is impossible for trains to arrive
     * from the west anyway, so both these conditions should disqualify the notice."*  Empty when the setup will not
     * build.
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

        // The builder's own mapping, as in badCopies.
        java.util.Map<String, TileKey> byName = named != null ? named : builder(null).tilesByName();

        org.json.JSONArray points = built.getJSONArray("points");

        java.util.Set<String> reached = copiesATrainReaches(built);

        for (int i = 0; i < points.length(); i++)
        {
            String name = points.getJSONObject(i).getString("name");

            if (hasOut.contains(name)) continue;

            TileKey tile = byName.get(name);

            if (tile == null) continue;

            // Not a plain point, nor an arrival no train makes (OB-361)
            if (!store.isStation(tile) || !reached.contains(name)) continue;

            // Turning is the way out of a square trains may turn at.
            if (isTurnAround(tile)) continue;

            // A square never split has no arrival to trap: with no way out it is a dead end.
            if (arrivalSides(tile).isEmpty()) continue;

            Side side = arrivals == null ? null : arrivals.get(name);

            // A barred side is no trap: closing it is advice the finding gives, and must silence it.
            if (side != null && getBarredArrivals(tile).contains(side)) continue;

            Set<Side> sides = out.computeIfAbsent(tile, k -> new LinkedHashSet<Side>());

            if (side != null) sides.add(side);
        }

        // A copy with no side named takes the square's ways in
        for (Map.Entry<TileKey, Set<Side>> square : out.entrySet())
        {
            if (square.getValue().isEmpty()) square.getValue().addAll(arrivalSides(square.getKey()));
        }

        return out;
    }

    /** The copies a train can arrive at on the built railway, walking on from stations and standing trains (OB-361). */
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

    /** The trapped squares whose trapped sides the arrivals menu can close (`arrivalsCanBeClosed`). */
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
     * Whether the arrivals menu can close these sides too: a station with more than one way in keeps one open.  The
     * menu's rule, asked by the trapped-arrival finding too, so its advice is advice the menu takes.
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
     * The termini a train that cannot reverse can be backed into: reached after an odd number of turns.  A train rested
     * on a terminus sets off turned, and each reversing point turns it again (MT-245); never through a terminus or a
     * square out of service, never straight back from a reversing point (a stub).
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

                    // Not straight back from a reversing point: a stub
                    if (turnsHere && before != null && before.equals(square)) continue;

                    if (next.optBoolean("terminus", false))
                    {
                        if (odd && square != null && !square.equals(setOffFrom)) out.add(square);

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
     * The built graph as JSON for the copy checks, or null when the setup will not build: an unsaved session's build
     * has no edges and on a test machine reached Adam's real railway, and its louder problems are reported already.
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
     * Station copies a train can leave but from which it reaches no other station square (its own turning twin does not
     * count).  Adam: **"yes, extend the check to catch that shape too."**  Copies with no way out at all are left to
     * the check above.
     * @return the squares, mapped to the offending copy, empty when the setup will not build
     */
    public java.util.Map<TileKey, String> destinationCopiesReachingNoStation()
    {
        return destinationCopiesReachingNoStation(builtForInspection(), null);
    }

    /**
     * The same, over a graph somebody has already built.
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
            if (!onward.containsKey(copy)) continue;

            TileKey here = byName.get(copy);

            if (here == null) continue;

            if (reachesAStationElsewhere(copy, here, onward, stations, byName)) continue;

            if (out.containsKey(here)) continue;

            // Only where a sibling copy does get somewhere (`badCopies`).
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

    /** Whether a walk from one copy reaches a station on another square; stops at the first. */
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

    /** Whether another copy of the square is in `good` (copies with a way out, or in). */
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

    /** The generated configuration, in the format the autonomy model already reads. */
    public String buildConfiguration()
    {
        return builder(globals()).build();
    }

    /**
     * A builder configured from this setup: the one place that says what the railway is.
     * @param globals the run-wide settings, or null when only the naming is wanted
     * @return a fresh builder
     */
    public AutonomyBuilder builder(AutonomyBuilder.Globals globals)
    {
        return builder(reducer, globals);
    }

    /** The same over a reduction tried and not installed. */
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
     * What Mass Assign Lengths asks a length for, by square, keyed per answer in its order (OB-297): each piece is one,
     * each page's switches one, its crossings one.
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
        // A station's only: a demoted square keeps its guards, guarding nothing
        return store.isStation(station) ? store.getProtectingSignal(station) : null;
    }

    /**
     * @param station a station's square
     * @return the squares of every signal protecting it, in the order they were paired
     */
    public List<TileKey> getProtectingSignals(TileKey station)
    {
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
        return store.isStation(station) ? store.getEntrySignals(station) : new ArrayList<TileKey>();
    }

    /**
     * Replaces every signal guarding the way into a station (FR-096).
     * @param station a station's square
     * @param signals the signals' squares; empty or null unpairs
     */
    public void setEntrySignals(TileKey station, List<TileKey> signals)
    {
        store.setEntrySignals(station, signals);
        touched();
    }

    /** @return every square switched out of service (`active` FALSE), empty when none is */
    public java.util.Set<TileKey> shutTiles()
    {
        // FALSE.equals: absent, or not a boolean, means in service
        return new LinkedHashSet<>(
            tilesWhere((key, point) -> Boolean.FALSE.equals(point.opt("active"))));
    }
    /**
     * Which accessories protect each station, by name, read off the diagram: the running layout commands accessories,
     * the store pairs squares.  Signals gone or without an address are left out.
     * @return station square to accessory names
     */
    public Map<TileKey, List<String>> protectingSignalNames()
    {
        return signalNames(stationsOnly(store.getProtectingSignals()));
    }

    /** @return the entry-guard pairings (FR-096) as station square to accessory names */
    public Map<TileKey, List<String>> entrySignalNames()
    {
        return signalNames(stationsOnly(store.getEntrySignals()));
    }

    /**
     * Station square to signal squares, as station square to accessory names.
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
     * Only the entries about squares that are stations now: a demoted station keeps its settings for when it is
     * promoted (OB-291), and the build, checks and editor read through this.
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

    /** Which sides each station refuses to let trains arrive by; only the squares that restrict anything. */
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
        // A station's only, as `getProtectingSignal`
        Set<Side> barred = store.isStation(tile) ? store.getBarredArrivals(tile) : new LinkedHashSet<Side>();

        if (barred.isEmpty()) return barred;

        // Only sides that still exist: a stale one would count toward the menu's last-open-side rule, unseen.
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
     * The sides a train can arrive at a square by, from the split: a side no copy exists for cannot be restricted.
     * @return the arrival sides, in the order the build emits them
     */
    public List<Side> arrivalSides(TileKey tile)
    {
        return getStationIndex().arrivalSidesAt(tile);
    }

    /**
     * The sides a train may have ARRIVED by (OB-204): `arrivalSides` less the barred ones, which nothing arrives by.
     * The geometry is for the "Trains May Arrive" menu, which must offer a barred side to un-bar it.
     * @param tile the square
     * @return the sides, in the build's own order, with the barred ones left out
     */
    public List<Side> unbarredArrivalSides(TileKey tile)
    {
        List<Side> sides = new java.util.ArrayList<>(arrivalSides(tile));

        sides.removeAll(getBarredArrivals(tile));

        // Every side barred (a diagram edit can do it): the geometry, not an empty list
        return sides.isEmpty() ? arrivalSides(tile) : sides;
    }

    /**
     * The diagram's name for a running Point: a split tile's copies share the base name its caption carries.
     * @param pointName a Point of the running configuration
     * @return the base name, or the name itself when nothing was split
     */
    public String baseNameOf(String pointName)
    {
        return pointName == null ? null : getStationIndex().baseNameOf(pointName);
    }

    // Emitted Point name to caption, asked on every feedback event; dropped on rebuild.  Volatile: the feedback thread
    // reads it while the event thread replaces it.
    private volatile StationIndex stationIndex;

    /**
     * The translation between squares and their Points, derived once per setup change so its answers cannot disagree.
     * @return the index, never null
     */
    public StationIndex getStationIndex()
    {
        StationIndex current = stationIndex;

        // Only before the first rebuild, when nothing runs; otherwise derived eagerly (`deriveStationIndex`).
        if (current == null)
        {
            current = deriveStationIndex();
        }

        return current;
    }

    /**
     * Derives the index now, on the event thread, from the two places that invalidate it: the volatile field publishes
     * the reference, not the derivation over HashMap-backed JSON, so readers must only see a finished index.
     * @return the new index, also stored
     */
    private StationIndex deriveStationIndex()
    {
        AutonomyBuilder naming = reducer == null ? null : builder(null);

        StationIndex derived = naming == null ? StationIndex.EMPTY : new StationIndex(naming);

        stationIndex = derived;

        // And the stored names carried to this build's: here, as not every renaming edit rebuilds (a may-turn mark adds
        // named turning copies).
        if (naming != null) carryTheNamesAcross(naming);

        return derived;
    }

    /**
     * Every copy of a station and which way a train on it would face; the first copy may be one that cannot leave.
     * @param tile the station's square
     * @return each copy's name to its train's facing, in build order; empty with no setup or when not a Point
     */
    public Map<String, Side> facingsFor(TileKey tile)
    {
        return getStationIndex().facingsAt(tile);
    }

    /**
     * Whether two Points were built from one square (copies facing apart are no journey).  Asked here: the running
     * layout's obvious key, the sensor, is shared by a station and its approach guard.
     * @param a a point name from the built graph
     * @param b another
     * @return true when both were built from one square
     */
    public boolean sameSquare(String a, String b)
    {
        if (a == null || b == null) return false;

        return a.equals(b) || getStationIndex().sameSquare(a, b);
    }

    /**
     * Every running Point that stands for one square of the diagram.
     * @param baseName the caption on the diagram
     * @return the emitted names, in the order they were emitted
     */
    public List<String> pointNamesFor(String baseName)
    {
        return getStationIndex().pointNamesFor(baseName);
    }

    /**
     * The square a running Point stands on, split copies included: the inverse of the builder's naming.
     * @param pointName a Point of the running configuration
     * @return its square, or null if this setup has never emitted that name
     */
    public TileKey tileForPointName(String pointName)
    {
        return getStationIndex().squareOf(pointName);
    }

    /**
     * The BASE name the generated configuration gives a square's Point, not the authored name (blank or duplicate).
     * @return the name, or null when the square is not a Point
     */
    public String pointNameForTile(TileKey tile)
    {
        return getStationIndex().nameOf(tile);
    }

    /**
     * What to call a square in front of the user: its authored name, then its sensor, then where it is.  One copy for
     * both windows.
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
     * Squares where a train may turn round, emitted as several Points each: a TERMINUS station (as one Point it would
     * block every path through it), and anything marked CAN REVERSE.
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

    /** The stations that are parking berths. */
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

    /** Whether every train arriving here must turn round, rather than merely being able to. */
    public boolean isMustTurnAround(TileKey tile)
    {
        return Boolean.TRUE.equals(getPointProperty(tile, AutonomyBuilder.MUST_REVERSE));
    }

    /**
     * The squares where a run may change direction: reversible minus compulsory.  One source for the path test and the
     * reachability check.
     * @return the may-turn set
     */
    public Set<TileKey> mayTurnTiles()
    {
        Set<TileKey> out = new LinkedHashSet<>(reversibleTiles());
        out.removeAll(mandatoryTurnTiles());
        return out;
    }

    /**
     * The stations autonomy never chooses (OB-195), by the square's clauses of `Layout.isSendableDestination`:
     * `isAutoDestination` (**Can Be Chosen in Full Autonomy**) and `isActive`.  `isMustTurnAround` is not a clause.
     * Adam: **"narrow the notice to match the runtime - autonomy should only allow a turn at a point if the 'allow in
     * autonomy' option is checked, otherwise the train may only pass through in its current direction."**
     * @return the tiles that are stations autonomy is told to leave alone
     */
    public Set<TileKey> stationsAutonomyWillNotChoose()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            // The runtime's clauses only (OB-195): `isMustTurnAround` decides no destination.
            //
            // Shut counts too: `isSendableDestination` requires `isActive()`.
            boolean shut = Boolean.FALSE.equals(getPointProperty(tile, "active"));

            if (store.isStation(tile) && (shut || !isAutoDestination(tile))) out.add(tile);
        }

        return out;
    }

    /**
     * The stations autonomy leaves alone that a person can still drive to: manual-only, not shut.  Adam: *"just use a
     * different color going to manual-only points"*.
     * @return the stations a hand-driven send may still reach and autonomy will not choose
     */
    public Set<TileKey> manualOnlyStations()
    {
        Set<TileKey> out = new LinkedHashSet<>(stationsAutonomyWillNotChoose());

        out.removeAll(shutTiles());

        return out;
    }

    /** The squares where turning round is compulsory. */
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
     * Whether trains may turn round on this square, reading the older "terminus" and "reversing" keys too, or an older
     * setup would lose its termini at the first rebuild.  The first edit writes the new spelling.
     */
    public boolean isTurnAround(TileKey tile)
    {
        if (isMustTurnAround(tile)) return true;

        if (Boolean.TRUE.equals(getPointProperty(tile, AutonomyBuilder.CAN_REVERSE))) return true;

        // A terminus turns trains only while the square is a station; the flag is kept for promotion
        if (store.isStation(tile) && Boolean.TRUE.equals(getPointProperty(tile, "terminus"))) return true;

        // A non-station "reversing" point turned trains on the way past
        return Boolean.TRUE.equals(getPointProperty(tile, "reversing")) && !store.isStation(tile);
    }

    /** Whether this station is a parking berth: never chosen by autonomy, never routed through. */
    public boolean isParking(TileKey tile)
    {
        if (!store.isStation(tile)) return false;

        // The switch as stored now (written only when off), then the older "parking", then a reversing STATION
        if (Boolean.FALSE.equals(getPointProperty(tile, AutonomyBuilder.AUTO_DESTINATION))) return true;

        if (Boolean.TRUE.equals(getPointProperty(tile, AutonomyBuilder.PARKING))) return true;

        return Boolean.TRUE.equals(getPointProperty(tile, "reversing"));
    }

    /** Whether full autonomy may choose this station: the switch the user sees. */
    public boolean isAutoDestination(TileKey tile)
    {
        return !isParking(tile);
    }

    /** Sets whether autonomy may choose this station, clearing the two older spellings of the same idea. */
    public void setAutoDestination(TileKey tile, boolean on)
    {
        setPointProperty(tile, AutonomyBuilder.AUTO_DESTINATION, on ? null : Boolean.FALSE);
        setPointProperty(tile, AutonomyBuilder.PARKING, null);
        setPointProperty(tile, "reversing", null);
    }

    /** Sets one of the three switches (CAN_REVERSE or PARKING), clearing the older spellings. */
    public void setPointFlag(TileKey tile, String key, boolean on)
    {
        setPointProperty(tile, key, on ? Boolean.TRUE : null);

        // Derived at build now; one left behind would outlive the switch that set it.
        setPointProperty(tile, "terminus", null);
        setPointProperty(tile, "reversing", null);
    }

    /** The same, laid out like the track it came from, for looking at in the graph window. */
    public String buildConfigurationForInspection()
    {
        List<String> pageOrder = new ArrayList<>();

        for (LayoutDiagram page : pages)
        {
            if (!store.getExcludedPages().contains(page.getName())) pageOrder.add(page.getName());
        }

        return builder(globals()).withCoordinatesFromTiles(pageOrder).build();
    }

    /** The per-point operational data of the active configuration, for the builder to merge in. */
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
     * Lifts what the running layout knows into the active configuration (placements, homes, termini, pace), so what was
     * set while running loads next time.  Takes `toJSON`'s output, the trusted serialization.  Keyed by square, so a
     * renamed Point keeps its placements; names matching no square are dropped.
     * @param layoutJson what the running Layout serialized to
     */
    public void captureFromLayout(String layoutJson)
    {
        captureFromLayout(layoutJson, store.getActiveConfiguration());
    }

    /**
     * The same, into a named configuration: after a refused load the store's active one never ran.
     * @param layoutJson what the running Layout serialized to
     * @param configurationName which configuration this layout's state belongs to
     */
    public void captureFromLayout(String layoutJson, String configurationName)
    {
        capture(layoutJson, configurationName, false, null);
    }

    /**
     * The same, bringing the record of how the railway was built up to what the fold wrote (OB-343).
     * @param layoutJson what the running Layout serialized to
     * @param configurationName which configuration this layout's state belongs to
     * @param asBuilt the railway's settings as it was built, changed in place to what the fold wrote; or null
     */
    public void captureFromLayout(String layoutJson, String configurationName, org.json.JSONObject asBuilt)
    {
        capture(layoutJson, configurationName, false, asBuilt);
    }

    /**
     * `captureFromLayout`, or with `placementsOnly` just each square's train, its tail's side and road, and its facing.
     * @param asBuilt the railway's settings as it was built, changed in place to the settings written; or null
     */
    private void capture(String layoutJson, String configurationName, boolean placementsOnly, org.json.JSONObject asBuilt)
    {
        if (layoutJson == null || reducer == null || configurationName == null) return;

        // Not after a rename or move (MT-135): this session's naming is stale and would place every train twice.
        // Nothing is lost: the store was written and the session is about to be rebuilt.
        if (pagesStale || squaresMoved) return;

        org.json.JSONObject configuration = store.getConfiguration(configurationName);

        if (configuration == null) return;

        org.json.JSONObject root = new org.json.JSONObject(layoutJson);

        Map<String, TileKey> tilesByName = new LinkedHashMap<>();

        AutonomyBuilder naming = builder(null);

        tilesByName.putAll(naming.tilesByName());

        Map<String, TilePorts.Side> facings = naming.facingByName();

        Set<TileKey> diagram = squaresOfTheDiagram();

        org.json.JSONObject points = new org.json.JSONObject();

        if (root.has("points"))
        {
            for (Object o : root.getJSONArray("points"))
            {
                org.json.JSONObject point = (org.json.JSONObject) o;

                // By the square the running Point copies, not today's naming of its name, which may have moved.
                TileKey tile = squareOf(point, diagram);

                if (tile == null) tile = tilesByName.get(point.optString("name"));

                if (tile == null) continue;

                org.json.JSONObject extras = new org.json.JSONObject();

                for (String key : placementsOnly ? PLACEMENT_KEYS : POINT_OPERATIONAL_KEYS)
                {
                    if (!point.has(key) || point.isNull(key)) continue;

                    // Only WHICH locomotive: `Point.toJSON`'s length, speed and functions would revert LocDB edits.
                    if ("loc".equals(key) && point.get(key) instanceof org.json.JSONObject)
                    {
                        org.json.JSONObject loc = point.getJSONObject(key);

                        if (!loc.has("name")) continue;

                        extras.put(key, new org.json.JSONObject().put("name", loc.getString("name")));

                        continue;
                    }

                    // A tail belongs to the copy the train stands on; the other copies keep a departed train's.
                    if (("arrivedFrom".equals(key) || "arrivedAlong".equals(key)) && !point.has("loc")) continue;

                    extras.put(key, point.get(key));
                }

                // The facing: the copy's own where the railway says, else the naming's.
                TilePorts.Side facing = sideNamed(point.optString(AutonomyBuilder.COPY_FACING, null));

                if (facing == null && tile.equals(tilesByName.get(point.optString("name"))))
                {
                    facing = facings.get(point.optString("name"));
                }

                if (facing != null && extras.has("loc"))
                {
                    extras.put(AutonomyBuilder.FACING, facing.name());
                }

                // And a home's facing where the railway holds the home to a copy (OB-282)
                if (facing != null && extras.has("home") && point.optBoolean(AutonomyBuilder.HOME_FACING_FIXED, false))
                {
                    extras.put(AutonomyBuilder.HOME_FACING, facing.name());
                }

                // Recorded even empty, so the merge removes a train that drove away.  Merged, not replaced: a split
                // square is visited once per copy and only one carries the locomotive.
                String id = tile.toString();

                org.json.JSONObject into = points.has(id)
                    ? points.getJSONObject(id) : new org.json.JSONObject();

                for (String key : extras.keySet()) into.put(key, extras.get(key));

                points.put(id, into);
            }
        }

        // Merged per point: the running layout predates the editor's edits, which must survive.
        org.json.JSONObject existing = configuration.has("points")
            ? configuration.getJSONObject("points") : new org.json.JSONObject();

        // A train the railway cannot stand (its facing has no copy) stays where the setup has it, unless the railway
        // has it elsewhere.
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

            // And an old occupant's tail leaves with it: read before `loc` is replaced.
            String occupantWas = nameOfPlacedLocomotive(before.opt("loc"));
            String occupantNow = nameOfPlacedLocomotive(captured.opt("loc"));

            if (occupantWas != null && !occupantWas.equals(occupantNow))
            {
                before.remove("arrivedFrom");
                before.remove("arrivedAlong");
            }

            // Replaced, or removed where the layout no longer carries them (back to default).
            for (String key : placementsOnly ? PLACEMENT_KEYS : POINT_OPERATIONAL_KEYS)
            {
                if (captured.has(key)) before.put(key, captured.get(key));
                else before.remove(key);
            }

            // Facing, from which copy the train was found on; never cleared, as the best guess for the next train.
            if (captured.has(AutonomyBuilder.FACING))
            {
                before.put(AutonomyBuilder.FACING, captured.get(AutonomyBuilder.FACING));
            }

            // And a home's facing (OB-282): kept where the layout's home has none, gone with the home.
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

        // Only a square whose TILE is gone loses its entry; a disconnected station keeps its placement for when the
        // track is joined again, and the check reports it.
        Set<String> stillThere = new LinkedHashSet<>();

        for (TileKey tile : graph.getTiles().keySet()) stillThere.add(tile.toString());

        // Squares on pages out of autonomy are not judged: excluding a page must be reversible.
        Set<String> pagesInPlay = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            // Nor a stand-in for an unreadable page, whose squares would all look gone (MT-135).
            if (page.isUnreadable()) continue;

            if (!store.getExcludedPages().contains(page.getName())) pagesInPlay.add(page.getName());
        }

        List<String> gone = new ArrayList<>();

        for (String id : existing.keySet())
        {
            if (stillThere.contains(id)) continue;

            TileKey tile = AutonomyCompanionStore.parseTileKey(id);

            if (tile == null || !pagesInPlay.contains(tile.getPage())) continue;

            gone.add(id);
        }

        if (!placementsOnly) for (String id : gone) existing.remove(id);

        configuration.put("points", existing);

        if (placementsOnly)
        {
            dirty = true;

            return;
        }

        org.json.JSONObject globals = globalsOf(root);

        configuration.put("globals", globals);

        // And the record of how the railway was built is the railway's settings now (OB-343)
        if (asBuilt != null)
        {
            for (String key : new ArrayList<>(asBuilt.keySet())) asBuilt.remove(key);

            for (String key : globals.keySet()) asBuilt.put(key, copyOfSetting(globals.get(key)));
        }

        dirty = true;
    }

    /** The top of a running layout's file: pace, speeds, the rest of the settings panel, and the timetable. */
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
     * Folds what the railway owns and nothing the setup gained since it was built: train placements and the settings
     * and timetable a run writes.  For a setup newer than the railway.
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
     * Folds the railway's settings and timetable alone, before a rebuild throws them away.  With a record of how the
     * railway was built, only those it changed since (OB-343), and the record follows what is taken, so it stays what
     * railway and setup last agreed on.  Paused trains are taken BY TRAIN against the record: gains and losses applied
     * to the setup's own list, never replacing it.
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
            if (asBuilt != null && asBuilt.has(key) && sameSetting(asBuilt.get(key), now.get(key))) continue;

            // Not the timetable while pages are stale: the railway still has the old Point names.
            if ((pagesStale || squaresMoved) && "timetable".equals(key)) continue;

            if (org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES.equals(key))
            {
                takePausesByTrain(configurationName, globals, asBuilt == null ? null : asBuilt.opt(key), now.get(key),
                    asBuilt == null);
            }
            else
            {
                globals.put(key, now.get(key));

                takenFromTheRailway(configurationName, key, now.get(key));
            }

            if (asBuilt != null) asBuilt.put(key, copyOfSetting(now.get(key)));
        }

        // And one the railway stopped writing (Simulate and the sequential flag are written only while on)
        if (asBuilt != null)
        {
            for (String key : new ArrayList<>(asBuilt.keySet()))
            {
                if (now.has(key)) continue;

                if (org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES.equals(key))
                {
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
     * Takes the railway's paused trains into the setup's list by train (OB-343).
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
     * Notes a setting a fold took during an edit session (`takenFromTheRailway`); JSON null where it stopped writing.
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

    /** A setting's value as a copy, so the record never shares an array with the setup. */
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

    /** Named squares on pages out of autonomy: they still take their turn in the naming. */
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

    /** The square of the diagram a running Point says it is a copy of, where the diagram has it, or null. */
    private static TileKey squareOf(org.json.JSONObject point, Set<TileKey> diagram)
    {
        String square = point.optString("square", null);

        TileKey tile = square == null ? null : AutonomyCompanionStore.parseTileKey(square);

        return tile != null && diagram.contains(tile) ? tile : null;
    }

    /** Every square of the diagram, including pages out of autonomy. */
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
     * Clears the in-use configuration's timetable, and saves.
     * @return whether there was a configuration to clear
     * @throws IOException if the setup cannot be written
     */
    public boolean clearTheTimetable() throws IOException
    {
        return setGlobal("timetable", new org.json.JSONArray());
    }

    /**
     * Writes one run-wide setting into the active configuration and saves at once.  Targeted: `captureFromLayout` would
     * copy every train's place too.  Callers must check the answer before dropping their own copy.
     * @param key the global's name, as it appears at the top level of the built configuration
     * @param value the value to store
     * @return true when the value was written and saved, false when there was no configuration to write it to
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

        // Without reconciling: no page was touched
        saveWithoutReconciling();

        return true;
    }

    /**
     * The trains this configuration pauses, by name, sorted (FR-117; Adam: "in autonomy configs, track the
     * paused/unpaused status of locomotives, as designated on the autonomy locomotive controls tab").  Kept under
     * `Layout.PAUSED_LOCOMOTIVES`, so build and fold carry it like any setting.
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
     * Records a train paused or set going in the active configuration, and saves (FR-117).
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
     * The same for several trains, saved once (FR-126).
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
     * Pauses a train or sets it going (FR-117): the train and this configuration's record together, so a fold between
     * them cannot take one for the other.
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
     * The same for several trains, saved once (FR-126).
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
     * What the active configuration STORES for a global, or null.  Not the live Layout, which answers with whatever was
     * last pushed into it, so a migration would be told yes by its own write.
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

    /** The globals of the active configuration: pace and speed settings. */
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
     * Whether a square can meaningfully be given a direction.  False for a link: its route is a stub, so "toward A" and
     * "toward B" are the same place; a one-way link would need the jump to carry it.
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

    /** Stations with no protecting signal at all: ordinary, listed rather than warned about. */
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
     * Squares whose recorded facing no copy can hold: the build falls back to `startableCopy` and the train quietly
     * turns.  Reported, not corrected, as only the operator knows which way it points; squares with no facings are left
     * to their own findings.
     */
    private java.util.Set<TileKey> facingsThatCannotBeHeld()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        // Where the railway has the train, as the warning and the Facing menu ask
        Map<String, Object[]> railway = railwayTrains();

        for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())
        {
            // A record the last run outran is no train there: the railway wins (OB-183)
            Object[] there = railway.get(placed.getValue());

            if (there != null && !placed.getKey().equals(there[0])) continue;

            // On the same square, its facing on the railway: a run can turn it and bring it back
            Side recorded = there != null && there[1] != null ? (Side) there[1] : getFacing(placed.getKey());

            if (recorded == null) continue;

            List<Side> offered = facingChoices(placed.getKey());

            if (offered.isEmpty() || offered.contains(recorded)) continue;

            // Not excused for having turned: a turning copy's facing is an arrival side `facingChoices` offers
            // (OB-145).
            out.add(placed.getKey());
        }

        return out;
    }

    /**
     * Stations whose paired signal no longer resolves to an accessory: the build drops it silently, and the operator
     * would believe the platform protected.
     */
    private java.util.Set<TileKey> signalsThatAreGone()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        if (graph == null) return out;

        // Any of them, not all: one gone signal leaves one approach unprotected.  Both guards (FR-096); stations only.
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
            // Not a station on an excluded page: the graph lacks it, so its signal would read as gone.
            if (store.getExcludedPages().contains(pair.getKey().getPage())) continue;

            // A signal on an excluded page IS gone from the build, so it is reported: never silence this warning
            // wrongly.
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
     * Whether every train arriving here must turn round (OB-123), so "may change direction here" offers no choice.
     * Departures as `departureSides` gathers them, as the trapped-arrival check does.
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

        // Nothing arrives: the unreachable-square checks' business.
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

            if (!onwards.isEmpty()) return false;
        }

        return true;
    }

    /**
     * The squares of the active configuration whose stored point passes a test: the one walk the home and shut queries
     * share, so they agree.  Keys split on the LAST colon (the store's rule), as page names may hold one.
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

    /** @return what `homeTiles()` answers, for a test that it matches `tilesWithAHome()` */
    public java.util.Set<TileKey> homesForFindings()
    {
        return homeTiles();
    }

    /** `homesElsewhere` under a name a test can call. */
    public java.util.List<TileKey> homesElsewhereForTest(TileKey tile, String locomotive)
    {
        return homesElsewhere(tile, locomotive);
    }
    /** Whether a stored point records a home locomotive (a blank is none). */
    private static boolean carriesAHome(org.json.JSONObject point)
    {
        return !point.optString("home", "").trim().isEmpty();
    }
    /** The squares an authored home lives at: `tilesWithAHome` as a set, so it cannot drift from `check()`. */
    private java.util.Set<TileKey> homeTiles()
    {
        return new LinkedHashSet<>(tilesWithAHome());
    }

    /**
     * The last check's findings, kept for the rest of the event that asked (one click asked twenty times) while nothing
     * `whatTheCheckReads` covers changes; off the event thread nothing is kept.
     */
    private java.lang.ref.WeakReference<java.awt.AWTEvent> findingsEvent;

    /** What the check read when the kept findings were worked out. */
    private List<Object> findingsKey;

    /** The kept findings; copied out, so each caller's list is its own. */
    private List<AutonomyChecks.Finding> findingsKept;

    /**
     * Everything check() reads that can change, as a key equal only while all of it is as it was: graph, reduction,
     * index, setup snapshot, railway and trains, placed lengths, every square.
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

    /** Everything wrong or worth knowing about the setup as it stands. */
    public List<AutonomyChecks.Finding> check()
    {
        // Kept for the rest of the event (`findingsEvent`)
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

    /** The check itself, every time: the findings, most severe first. */
    private List<AutonomyChecks.Finding> checkAfresh()
    {
        // A panel asks in its constructor, before open(); an empty list reads better than a throw.
        if (graph == null || reducer == null) return new ArrayList<AutonomyChecks.Finding>();

        // The terminus flag lives in the configuration, so the checks are told it
        Set<TileKey> termini = new LinkedHashSet<>();

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (store.isStation(tile) && Boolean.TRUE.equals(getPointProperty(tile, "terminus"))) termini.add(tile);
        }

        // "May turn round here" where no arriving train could do anything else (OB-123), judged per ARRIVAL against
        // the build's own sides.  Adam: "this is wrong: the train can continue or reverse.  It should test for two
        // outgoing paths, not two incoming."
        Set<TileKey> pointless = new LinkedHashSet<>();

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (!isTurnAround(tile) || isMustTurnAround(tile)) continue;

            if (everyArrivalMustTurn(graph, reducer, tile)) pointless.add(tile);
        }

        // Built once: the checks ask the build rather than derive a second answer.  With an ERROR there is no build,
        // and those checks reason about the diagram.
        org.json.JSONObject inspected = builtForInspection();

        java.util.Map<String, TileKey> namesForInspection =
            inspected == null ? null : builder(null).tilesByName();

        GuardNotices guards = guardNotices(inspected, namesForInspection);

        // and each copy's arrival side, which names a trapped arrival (OB-201)
        java.util.Map<String, Side> arrivalsForInspection =
            inspected == null ? null : builder(null).arrivalByName();

        // Squares a train can reach and then not leave, off the built graph
        Map<TileKey, Set<Side>> trapped = tilesWithATrappedArrival(inspected, namesForInspection, arrivalsForInspection);

        // Captions sitting over the user's own text, which the grid hides (MT-017)
        Map<TileKey, TileKey> covered = new LinkedHashMap<>();

        for (Map.Entry<TileKey, TileKey> caption : store.getCaptions().entrySet())
        {
            // Not one kept for a non-station: it is not drawn
            if (caption.getValue() == null || !store.isStation(caption.getValue())) continue;

            LayoutDiagram page = pageOf(caption.getKey());

            if (page == null) continue;

            LayoutDiagramComponent component =
                page.getComponent(caption.getKey().getX(), caption.getKey().getY());

            if (component == null || component.getLabel() == null) continue;

            if (!component.getLabel().trim().isEmpty()) covered.put(caption.getKey(), caption.getValue());
        }


        java.util.Set<TileKey> givenNoRoom = new LinkedHashSet<>();

        java.util.Map<TileKey, int[]> runIns = runInFigures(givenNoRoom, inspected, namesForInspection);

        List<AutonomyChecks.Finding> found = AutonomyChecks.run(graph, reducer, termini, getLabelledStationTiles(), pointless,
            trapped, arrivalsClosableAt(trapped), covered, placedLocomotives(), shutStations(),
            mayTurnTiles(), mandatoryTurnTiles(), homeTiles(), signalsThatAreGone(),
            stationsWithNoSignal(), facingsThatCannotBeHeld(),
            // Red arrows and shut squares, so the findings walk the usable railway (OB-120)
            barredArrivals(),
            shutTiles(),
            // The length rule's two halves (FR-046), and their disagreements
            placedTrainsWithoutLength(), stationsWithoutMaxLength(), runIns, givenNoRoom,
            // And the half-measured approach, which closes a berth until finished
            stationsWithAHalfMeasuredApproach(),
            // A terminus with two ways in (MT-361)
            terminiWithTwoWaysIn(),
            // Pages sharing a sensor, which cannot be modelled (OB-150)
            repeatedSensorPages(),
            reversalsWithoutLength(),
            // Stations never chosen, so a pocket of only those still leads nowhere
            stationsAutonomyWillNotChoose(),
            berthsATrainCanBackInto(inspected, namesForInspection),
            // Per-copy checks, all off one build: three builds per right-click would be too slow
            destinationCopiesWithNoWayOut(inspected, namesForInspection),
            destinationCopiesWithNoWayIn(inspected, namesForInspection),
            destinationCopiesReachingNoStation(inspected, namesForInspection),
            guardsOnBothLists(), guards.entryOffTheWayIn, guards.exitOnNoRoute, guards.exitCrossed, guards.exitOnTheWayIn,
            restrictionsToList());

        // Said, not refused
        found.addAll(AutonomyChecks.checkTrainsFacingTrackTowardsThem(reducer, trainsFacingTrackTowardsThem()));
        java.util.Set<TileKey> endsOfLines = stationsAtTheEndOfALine();

        found.addAll(AutonomyChecks.checkStationsAtTheEndOfALine(reducer, endsOfLines));

        // A station named in more than one place (OB-341)
        found.addAll(AutonomyChecks.checkStationsNamedTwice(stationsShownMoreThanOnce()));

        // A run whose squares point opposite ways (OB-361)
        found.addAll(AutonomyChecks.checkRunsPointingOppositeWays(runsPointingOppositeWays()));

        // A sensor address two places share (FR-139)
        found.addAll(AutonomyChecks.checkSensorAddressesShared(sensorAddressesSharedByPlaces()));

        // One finding for a station at the end of a line: "may" would draw a notice saying "must"
        found.removeIf(finding -> AutonomyChecks.isArrivalTrapped(finding.getMessageKey())
            && endsOfLines.contains(finding.getTile()));

        AutonomyChecks.bySeverity(found);

        return found;
    }

    /** Runs whose squares point opposite ways, first square to squares, off stationless track (OB-361). */
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
     * Stations whose name is drawn twice on one page (OB-341; Adam: *"The duplicate caption check should be per page.
     * It is OK to show on another page what is going on on another."*), by `drawnCaptionsFor`, in reading order.  The
     * editor's doors prevent it, so these came from a hand-edited or older file.
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
            // Drawn is drawn, Point or not (OB-341).
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
     * Every square held back while another is occupied, to the names it watches; not on excluded pages.  Adam: *"a
     * simple info notice on restrictions (in the list for any type of station or non station)"*.
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
     * Stations whose entry guard is also their exit guard: the setters refuse it, a hand-written file need not.
     * @return station square to the names of the signals on both its lists
     */
    private Map<TileKey, Map<TileKey, String>> guardsOnBothLists()
    {
        Map<TileKey, Map<TileKey, String>> out = new LinkedHashMap<>();

        if (graph == null) return out;

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

    /** The guard notices, station square to the names of its guards, one map per notice. */
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
     * Which routes command each guard, read off the BUILT railway, whose edges know each copy's facing and the
     * accessories they command.  Adam: *"if the guard signal is not on a path leading to the chosen station, we can add
     * notice to the autonomy editor."*  Noticed: an entry guard no way in commands; an exit guard no route commands
     * (away from the station's own track), one routes to other stations command, and one a way in commands.  None with
     * no build.
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

            // Nor one that is not a station now
            if (!store.isStation(station)) continue;

            java.util.Set<Integer> in = waysAt(station, true, from, to, named, stops);
            java.util.Set<Integer> outOf = waysAt(station, false, from, to, named, stops);

            // A station no route reaches or leaves is cut off, which its own check says
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

                // Elsewhere: not a way in or out, nor over the station (unsettable under a standing train)
                java.util.Set<Integer> elsewhere = new java.util.HashSet<>();

                for (int i = 0; i < commands.size(); i++)
                {
                    if (!in.contains(i) && !outOf.contains(i) && !station.equals(named.get(from[i]))
                        && !station.equals(named.get(to[i]))) elsewhere.add(i);
                }

                // On a way in (OB-361), except where that way starts at a station this signal is the exit guard of
                java.util.Set<Integer> comingIn = new java.util.HashSet<>();

                for (Integer i : in)
                {
                    TileKey leaving = named.get(from[i]);

                    if (leaving == null || !store.isStation(leaving)
                        || !nonNull(store.getProtectingSignals().get(leaving)).contains(signal)) comingIn.add(i);
                }

                // No route at all is noticed only off the station's own track, where it shows occupancy
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
     * The built edges on a station's ways in (back from its stopping copies) or out (on from any copy), through every
     * point a train passes to the first stop.
     * @param in true for the ways in, false for the ways out
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

    /** Whether a signal lies on the track of a station's own reduced edges, its room past the sensor included. */
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

    private static boolean commandedBy(String accessory, java.util.Set<Integer> edges, List<java.util.Set<String>> commands)
    {
        if (accessory == null) return false;

        for (Integer edge : edges) if (commands.get(edge).contains(accessory)) return true;

        return false;
    }

    /** The accessory a signal's square carries, or null: routes command accessories by name. */
    private String accessoryOf(TileKey signal)
    {
        org.traincontrol.base.LayoutDiagramComponent component = graph == null ? null : graph.getTiles().get(signal);

        return component == null || component.getAccessory() == null ? null : component.getAccessory().getName();
    }

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
     * How many things must be dealt with before this setup will run: the one definition of "broken", the same count
     * that refuses a start (OB-057), so the strip never offers a Start that is refused.  Adam: "the fix it button is
     * not shown, rather just start autonomy when the config had worked before." (OB-090)
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
     * Whether the setup can be RUN (`errorCount`).  Not the load door's question, `hasBlockingProblems`: loading is how
     * somebody gets at the errors to fix them.
     */
    public boolean hasErrors()
    {
        return hasBlockingProblems() || errorCount() > 0;
    }

    /** Whether anything would stop this being built. */
    public boolean hasBlockingProblems()
    {
        return graph != null && graph.hasBlockingProblems();
    }

    /**
     * How many blocking problems there are, for the doors that pick singular or plural by it.
     * @return the number of problems that would stop the setup being built, or zero when there is no graph to ask
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
     * Records directions wanted on one square's roads as its arms can hold them, without re-deriving (OB-351): every
     * door comes through here, so none stores a road closed where the arrow shows it open.
     */
    private void recordSquare(TileKey tile, Map<RouteId, Direction> wanted)
    {
        Map<RouteId, Route> routes = graph.getRoutes(tile);

        for (Map.Entry<RouteId, Direction> answer : graph.armAnswersFor(tile, wanted).entrySet())
        {
            Route route = routes.get(answer.getKey());

            if (!wanted.containsKey(answer.getKey()) && route != null
                && TileGraph.inForce(graph.getDirection(tile, answer.getKey()), route) == answer.getValue()) continue;

            record(tile, answer.getKey(), answer.getValue());
        }
    }

    /** Records a direction without re-deriving, for callers that are about to set several. */
    void record(TileKey tile, RouteId routeId, Direction direction)
    {
        apply(tile, routeId, direction);

        // One track, two squares (OB-320): a two-square crossing's direction is set on both, in each square's terms.
        RouteId across = graph == null ? null : graph.crossingRouteOnPartner(tile, routeId);

        if (across != null)
        {
            apply(graph.crossingPartner(tile), across, graph.crossingDirectionOnPartner(tile, routeId, direction));
        }
    }

    /**
     * The directions recorded, re-derived, never refused under a standing train (Adam: *"Generally I prefer warnings or
     * errors over outright refusals, since it makes editing easier for the user"*).  Trains the change leaves on no
     * copy are first written into the setup where they stand.
     */
    boolean directionsTouched()
    {
        recordTrainsTheRailwayCannotHold();

        touched();

        return true;
    }

    /**
     * Trains the setup records on a square where the railway stands them on no Point, to their facing, drawn along the
     * track as `facingWhereNoCopySays` draws them (Adam: "it must be N/S or a N/S track, or E/W on E/W, or angled on a
     * curved track").
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

    /** The squares of `trainsOnNoPoint` whose train this configuration pauses, by the setup's list. */
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
     * Records in the setup each railway train the setup now gives no copy facing its way: the rebuild cannot move it to
     * another copy, so the check reports it and the fold keeps it on its square.
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

            // And its tail
            if (train.getValue().length > 2 && train.getValue()[2] != null) setArrivedFrom(square, (String) train.getValue()[2]);

            if (train.getValue().length > 3 && train.getValue()[3] != null)
            {
                setPointProperty(square, "arrivedAlong", new org.json.JSONArray((String) train.getValue()[3]));
            }
        }
    }

    /** Whether a naming has a copy a train facing that way can stand on, as `pointNamedNow` puts it back. */
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

    /** The railway's trains, each to its square and facing, or null: by the running Point's square, else its name. */
    private Map<String, Object[]> railwayTrains()
    {
        Map<String, Object[]> standing = new LinkedHashMap<>();

        org.traincontrol.automation.Layout running = runningLayout == null ? null : runningLayout.get();

        if (running == null) return standing;

        for (org.traincontrol.automation.Point point : running.getPoints())
        {
            Locomotive loc = point.getCurrentLocomotive();

            TileKey square = point.getSquare() == null ? null : AutonomyCompanionStore.parseTileKey(point.getSquare());
            Side facing = sideNamed(point.getCopyFacing());

            if (square == null) square = squareOfNameNow.get(point.getName());
            if (facing == null) facing = facingOfNameNow.get(point.getName());

            if (loc == null || loc.getName() == null || square == null) continue;

            standing.putIfAbsent(loc.getName(), new Object[] {square, facing, point.getArrivedFrom(),
                org.traincontrol.automation.Layout.namesOfRoad(point.getArrivedAlong())});
        }

        return standing;
    }

    /**
     * The trains standing, to square and facing, or null: the railway's, and the setup's where the railway has none.
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

    /** Squares where a standing train faces track that only runs towards it (a warning). */
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

    /** Stations at the end of a line that trains may not turn at (an error): a train sent there could never leave. */
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

    /** The naming of a trial reduction of the setup as it now stands, not installed. */
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

        // stored only where it differs from the graph's default, so a default never looks like a decision
        store.setTileDirection(tile, routeId,
            direction == graph.defaultDirection(tile, routeId) ? null : direction);
    }

    /**
     * Every route whose setting shuts a move the hardware allows (one-way, closed, switches at base-to-forks), for
     * Allow Every Path (FR-108).  A turnout with no address counts only if closed.
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

    /** Whether a route's setting refuses a move the hardware allows, as `TileGraph.exits` answers. */
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
     * Opens both ways every route that can be, re-deriving once (FR-108; Adam: *"add a bulk tool to allow all paths,
     * i.e. remove any red arrows in one go. With warning."*).
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

    /** Applies one direction to many tiles at once, re-deriving once. */
    public void setDirection(Set<TileKey> tiles, Direction direction)
    {
        // Recorded first, re-derived once: one rebuild per route would be forty on the event thread.
        for (TileKey tile : tiles)
        {
            Map<RouteId, Direction> every = new LinkedHashMap<>();

            for (RouteId routeId : graph.getRoutes(tile).keySet()) every.put(routeId, direction);

            recordSquare(tile, every);
        }

        directionsTouched();
    }

    /**
     * Applies a direction to each of one tile's routes, re-deriving once: a junction's branches need different
     * constants for one meaning (TOWARD_A names a route's own first side).
     * @param directions route to the direction it should take
     */
    public void setDirections(TileKey tile, Map<RouteId, Direction> directions)
    {
        recordSquare(tile, directions);

        directionsTouched();
    }

    /**
     * Tells autonomy to use, or to ignore, a link.
     * @param tile the link
     * @param disabled true to leave it out of the railway entirely
     */
    public void setPortalDisabled(TileKey tile, boolean disabled)
    {
        store.setPortalDisabled(tile, disabled);

        // Not refused under a standing train: it is recorded where it stands and the check reports it
        recordTrainsTheRailwayCannotHold();

        touched();
    }

    /** Renames a station; captions point at its square, so they follow. */
    public void setPointName(TileKey tile, String name)
    {
        // Refused, the guard behind every door that asks `whyNotAPointName` first; stored and imported names pass.
        String why = whyNotAPointName(name);

        if (why != null) throw new IllegalArgumentException(why);

        store.setPointName(tile, name);
        touched();
    }

    /**
     * Whether another square already has this name, in any case (FR-120).
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
     * Why a typed name may not be given to a square, or null: one ending in the builder's own heading ("Main
     * (eastbound)") reads as another square's copy.  Adam: *"refuse and close, but make sure the words are uncommon"*,
     * so only `StationIndex.endsWithAnArrivalHeading`'s exact form is refused.
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
        // A demoted square keeps its station settings (maximum train length, caption, barred arrivals, guards, the old
        // "terminus" flag) for promotion, ignored where read while it is not a station (OB-291; Adam: *"make max train
        // length be remembered if a station is changed to a non-station, and then restored if it is changed back to a
        // station.  don't modify the behavior of this attribute: it is still to be ignored for non-stations."*).
        store.setStation(tile, station);

        // Not unavailable-while-occupied, which stays in force on any square (Adam: "do allow restrictions on
        // non-stations, and let's not clear them when the type changes").

        touched();
    }

    /**
     * The name of the locomotive this configuration puts on a square (a placement is an object; see
     * `nameOfPlacedLocomotive`).
     * @param tile the square
     * @return the locomotive's name, or null when nothing is placed here
     */
    public String getLocomotiveNameAt(TileKey tile)
    {
        if (tile == null) return null;

        return nameOfPlacedLocomotive(getPointProperty(tile, "loc"));
    }

    /**
     * Whether this square can hold a name: only a Point of the reduced graph has one.  MT-313: *"it works on any tile
     * in the autonomy editor, not just sensors."*
     * @param tile the square
     * @return whether naming it means anything
     */
    public boolean canBeNamed(TileKey tile)
    {
        // The reducer's answer, so the menu and the key agree
        return tile != null && getReducer() != null && getReducer().getPoints().containsKey(tile);
    }

    /**
     * Puts a locomotive on a point keeping what is known about it: parseAuto RESETS whatever a placement omits.
     * @param name the locomotive, or null to clear the placement
     */
    public void placeLocomotive(TileKey tile, String name)
    {
        if (name == null)
        {
            setPointProperty(tile, "loc", null);

            // The facing, arrival side and road described the train, not the square, and would pass to the next one.
            setPointProperty(tile, AutonomyBuilder.FACING, null);

            setPointProperty(tile, "arrivedFrom", null);

            setPointProperty(tile, "arrivedAlong", null);

            return;
        }

        Object existing = getPointProperty(tile, "loc");

        org.json.JSONObject loc = existing instanceof org.json.JSONObject
            ? new org.json.JSONObject(existing.toString()) : new org.json.JSONObject();

        loc.put("name", name);

        // And off every other square: a train at two Points invalidates the WHOLE running layout.
        forgetPlacementsElsewhere(tile, name, loc);

        setPointProperty(tile, "loc", loc);

        // A new occupant does not inherit the old one's tail; the doors that know the side write it after this.
        if (!name.equals(nameOfPlacedLocomotive(existing)))
        {
            setPointProperty(tile, "arrivedFrom", null);
            setPointProperty(tile, "arrivedAlong", null);
        }
    }

    /**
     * Takes every locomotive off the setup, re-deriving once, through the single-square door's writer.
     * @return how many squares were cleared
     */
    public int clearEveryPlacement()
    {
        java.util.List<TileKey> placed = new java.util.ArrayList<>(placedLocomotives().keySet());

        for (TileKey tile : placed)
        {
            writePointProperty(tile, "loc", null);

            // The facing and arrival side go too, as in `placeLocomotive`.
            writePointProperty(tile, AutonomyBuilder.FACING, null);

            writePointProperty(tile, "arrivedFrom", null);
            writePointProperty(tile, "arrivedAlong", null);
        }

        // Once: the split names are computed from these properties.
        deriveStationIndex();

        return placed.size();
    }

    /** One thing a configuration held of a locomotive the database does not have, and let go of (OB-349). */
    public static final class LocomotiveLeftOut
    {
        /** What it held: the train on the square, its home there, or its name on the square's list. */
        public enum What { STANDING, HOME, EXCLUDED, ALLOWED }

        public final String locomotive;

        public final What what;

        public final TileKey square;

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
     * Takes every locomotive the database lacks out of a configuration (OB-349): placements with their facing and
     * arrival, homes with their facing, names on square lists.  The loader drops them from the railway anyway; left,
     * the setup would show and save trains the railway has not.  Timetable and paused trains are the loader's.
     * @param configuration the configuration's name
     * @param known the names the locomotive database holds
     * @return what was let go of, by locomotive and then square; empty when the configuration names none it lacks
     */
    public java.util.List<LocomotiveLeftOut> forgetLocomotivesNotIn(String configuration,
        java.util.Collection<String> known)
    {
        java.util.List<LocomotiveLeftOut> out = new java.util.ArrayList<>();

        // Nothing against an empty database, which would empty the setup and save it.
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

            // The train standing, with the keys `placeLocomotive` clears
            String standing = nameOfPlacedLocomotive(point.opt(AutonomyBuilder.LOCOMOTIVE));

            if (standing != null && !known.contains(standing))
            {
                point.remove(AutonomyBuilder.LOCOMOTIVE);
                point.remove(AutonomyBuilder.FACING);
                point.remove("arrivedFrom");
                point.remove("arrivedAlong");

                out.add(new LocomotiveLeftOut(standing, LocomotiveLeftOut.What.STANDING, square));
            }

            // The home, with the keys `clearEveryHome` clears
            String home = point.optString("home", "").trim();

            if (!home.isEmpty() && !known.contains(home))
            {
                point.remove("home");
                point.remove(AutonomyBuilder.HOME_FACING);

                out.add(new LocomotiveLeftOut(home, LocomotiveLeftOut.What.HOME, square));
            }

            // The square's list, either mode (FR-121); emptied as the editor empties it, mode kept.
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

        // The split names are computed from these properties
        if (configuration.equals(store.getActiveConfiguration())) deriveStationIndex();

        out.sort(java.util.Comparator.<LocomotiveLeftOut, String>comparing(left -> left.locomotive)
            .thenComparing(left -> left.what).thenComparing(left -> left.square.toString()));

        return out;
    }

    /**
     * The locomotive named by a stored placement, or null: the one place that knows its shape, an object (parseAuto
     * resets what it omits) or, in a hand-edited or old file, a bare string, read too so a re-placement is no change of
     * occupant.
     * @param placement whatever was stored under "loc"
     * @return the name, trimmed, or null
     */
    private static String nameOfPlacedLocomotive(Object placement)
    {
        if (placement == null) return null;

        String name;

        if (placement instanceof org.json.JSONObject)
        {
            // optString: a hand-edited file can omit the name.
            name = ((org.json.JSONObject) placement).optString("name", null);
        }
        else
        {
            // Nothing drawn on an occupied platform is the one wrong answer.
            name = String.valueOf(placement);
        }

        if (name == null) return null;

        name = name.trim();

        return name.isEmpty() ? null : name;
    }

    /**
     * Takes a locomotive off every other square, carrying what was recorded with it into the new placement.
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

            // And its tail, as the other placement doors clear it.
            extras.remove("arrivedFrom");
            extras.remove("arrivedAlong");

            swept = true;
        }

        // Once: `touched` rebuilds the whole graph.
        if (swept) touched();
    }

    /**
     * The ways a train on this square could point: one per copy the build emits, the ONWARD side of its road, not the
     * opposite of its entry (on a curve they differ, MT-125).  None where the build emits the square whole.  The first
     * is not what an unrecorded placement gets (`startableCopy`).
     * @return the possible facings; empty when nothing reaches the square, one when only one line does
     */
    public List<Side> facingChoices(TileKey tile)
    {
        // Asked of the build (`facingsFor`): a facing it cannot hold makes `placementCopy` quietly turn the train.  So:
        //
        // the onward side (MT-125); the arrival side on a turn-around square (OB-145); the plain copy first; nothing on
        // a square emitted whole.
        Set<Side> out = new java.util.LinkedHashSet<>(facingsFor(tile).values());

        return new ArrayList<>(out);
    }


    /**
     * The diagram tiles a standing train is lying across, for greying out.
     *
     * Adam: **"Locked tiles in this way should be greyed out until the train blocking it moves."**
     *
     * Asked of the railway: `Layout` decides which edges a train covers, joined back by square, both directions, end
     * squares excluded (Adam: *"edges, because the points are technically unoccupied"*).  Walked square by square only
     * as far as the train's length reaches (MT-309), a subset of what the railway holds, the safe direction.  Where the
     * train IS, not what is unavailable (`tilesBlockedByStandingTrains`).
     * @param running the layout, which is what knows where the trains are
     * @return the squares to draw as covered, empty when nothing is
     */
    public Set<TileKey> tilesCoveredByStandingTrains(org.traincontrol.automation.Layout running)
    {
        return routesCoveredByStandingTrains(running).keySet();
    }

    /**
     * The same, saying which road of each square the train is on (MT-309).  Adam: *"instead of shading the entire
     * tiles, we need to draw a line (let's say in orange) to show that the train is there.  graying makes it look
     * confusing on double curve tiles."*
     * @param running the layout, which is what knows where the trains are
     * @return each covered square, with the routes of it the train is lying across
     */
    public Map<TileKey, Set<RouteId>> routesCoveredByStandingTrains(
        org.traincontrol.automation.Layout running)
    {
        Map<TileKey, Set<RouteId>> out = new LinkedHashMap<>();

        if (running == null || reducer == null || getStationIndex() == null) return out;

        // By train, as the walk goes back from each, and kept as the reduction's own edges (OB-239): the end squares
        // alone cannot tell two roads between one pair of sensors apart.
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

            // Once each: several railway copies of a rail are one reduced edge
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
     * Draws the trains the walk above cannot (a tail stopping at a fork covers no edge, OB-290) from their claimed
     * places (MT-477), along the road every rail over the claims agrees on (OB-348); where they disagree, no line.
     */
    private void drawTheTrainsThatCoverNoEdge(org.traincontrol.automation.Layout running,
        Set<org.traincontrol.base.Locomotive> drawn, Map<TileKey, Set<RouteId>> out)
    {
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

            // Every road each claimed square is run along: a step by its route, the arrival square by the side reached.
            Map<TileKey, Set<RouteId>> seen = new LinkedHashMap<>();

            // And how far back from the platform each lies, along the rail
            Map<TileKey, Integer> back = new LinkedHashMap<>();

            List<GraphReducer.ReducedEdge> rails = railsATailLiesAlong(running, train.getKey(), claimed);

            // Measured only on rails into the platform; on the every-edge fallback nothing is cut
            boolean fromThePlatform = rails != null;

            for (GraphReducer.ReducedEdge edge : fromThePlatform ? rails : reducer.getEdges())
            {
                List<GraphReducer.Place> places = reducer.placesAlong(edge);

                List<GraphReducer.TileStep> path = edge.getPath();

                // One place per step, then the arrival square: `placesAlong`'s shape.
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

            // Nothing past the first square whose road is unknown (Adam: "stop at unknown.  but if it's not thrown and
            // the train must pass through the thrown arm, then do draw it"); a switch every rail crosses on one arm is
            // known.
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

                // One road or none: two would put the train on both legs.
                if (square.getValue().size() == 1) roads.addAll(square.getValue());
            }
        }
    }

    /**
     * The rails a train with no covered edge can lie along (OB-348): every reduced rail into its square, by its arrival
     * side where known, running over every claimed place, including rails the build leaves out (a hand placement may
     * have come along one).
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
     * The tiles standing trains have BLOCKED, for greying (OB-280): the places `Layout.placesCoveredByStandingTrains`
     * claims, as squares.  Adam: *"'train is here' should also mean 'track is blocked' - that is the whole point.  it's
     * the same as greying out edges, just in a different way."*  Whether autonomy is running is the caller's question.
     * @param running the layout, which is what knows where the trains are
     * @return the squares to grey, empty when nothing is blocked
     */
    public Set<TileKey> tilesBlockedByStandingTrains(org.traincontrol.automation.Layout running)
    {
        return routesBlockedByStandingTrains(running).keySet();
    }

    /**
     * The same, saying which road of each square is blocked (OB-280).  Adam: *"grey what routing actually refuses.  if
     * the train doesn't protrude past the switch, there should be nothing else to gray."*  Translated by the ids
     * `GraphReducer.placesAlong` built; per road where the place is per road (double curve, overpass), else the whole
     * square, no road named.
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

                // One place per step, then the arrival square: `placesAlong`'s shape.
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

                // A place naming a road blocks that road; one naming the square, all of it.
                if (onTheWay && id.indexOf('/') >= 0 && path.get(at).getRouteId() != null)
                {
                    roads.add(path.get(at).getRouteId());
                }
            }
        }

        return out;
    }

    /**
     * Paints one train's length back along the edges the railway holds covered (MT-309, OB-239), square by square,
     * spending each square's length until it is used up; unmeasured squares cost nothing.
     */
    private void walkBackFrom(org.traincontrol.automation.Layout running,
        org.traincontrol.base.Locomotive train, List<GraphReducer.ReducedEdge> covered,
        Map<TileKey, Set<RouteId>> out)
    {
        if (train == null || train.getTrainLength() == null) return;

        int remaining = train.getTrainLength();

        if (remaining <= 0) return;

        // Where the train IS (`whereTheTrainIs`), not the first Point of its locked path
        org.traincontrol.automation.Point standing = running.whereTheTrainIs(train);

        if (standing == null) return;

        TileKey at = getStationIndex().squareOf(standing.getName());

        if (at == null) return;

        Set<TileKey> walked = new LinkedHashSet<>();

        walked.add(at);

        // The standing square is spent first, as the guard spends it (OB-278; Adam: *"the 2 length tile with the s88
        // consumes 2 units of the train"*).
        while (remaining > 0)
        {
            // The next covered edge along (OB-239); `walked` stops a loop of track doubling back.
            GraphReducer.ReducedEdge rail = nextCoveredEdge(covered, at, walked);

            if (rail == null) return;

            TileKey next = at.equals(rail.getEnd()) ? rail.getStart() : rail.getEnd();

            // Sensor squares are drawn too (OB-277; Adam: *"when we draw orange lines, they don't overlap with
            // sensors"*); the standing square once the first hop says which side the body leaves by.
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

                // Null for a portal hop: covered, road unknown.
                if (step.getRouteId() != null) roads.add(step.getRouteId());

                remaining -= store.getTileLength(step.getTile());

                if (remaining <= 0) return;
            }

            // The far end is the next square back, drawn along the road facing the track just walked (OB-277)
            markTheSensor(out, next, rail);

            remaining -= store.getTileLength(next);

            walked.add(next);

            at = next;
        }
    }

    /**
     * The reduction's own edge for a covered railway edge, matched by its places in order (OB-239), or by squares and
     * direction where it carries none; null where none matches (a railway built before a redraw), not drawn.
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
     * The covered edge the walk goes on along, or null (OB-239): one ending here first (as `Layout.walkOneTail`), then
     * one starting here; never back to a square walked.
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
     * A covered edge's steps, ends excluded, in the order the body lies from this end: the order the length is spent
     * in, each step naming its road (MT-309).
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
     * Puts a sensor square a train lies on into the covered set with its road (OB-277); with no road nameable (a portal
     * hop) it goes in and draws no line.
     * @param rail the covered edge the body lies along there, whose own side names the road (OB-239)
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
     * Which road of a square uses this side, or null: that edge's own road, even on a double curve (OB-239, MT-309).
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
     * Whether a train standing anywhere on this square could move at all.
     *
     * About the SQUARE, not one copy: a dead copy beside a live one must not decide it
     * (`LayoutRightclickAutonomyMenu.placeableCopies` asks the same).
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
     * direction.  paste with the direction where the train ends up at the destination.  if no path pick randomly from
     * the allowed departure destinations."**  The track decides the heading, so the train is driven: a shortest walk
     * from its copy arrives at a copy of the target, whose side is the answer.  With no path its heading is kept where
     * the landing holds it (Adam: *"as long as the direction isnt flipped (which it was before)"*), else the first
     * departable copy.  Deterministic (`core.testAPasteDoesNotTurnTheTrainRound`).
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

        // The heading it already has, where the landing holds it (Adam: *"Option 1 is fine as long as the direction
        // isnt flipped (which it was before)."*)
        Side already = facingOf(locomotive, running);

        if (already != null && departable.contains(already)) return already;

        // The first legal one, not a random one (Adam: "in all your simulations, state should never drift")
        return departable.get(0);
    }

    /**
     * The breadth-first half: from where this train stands to the nearest copy of the target a train could stand on.
     * `getNeighbors` shuffles, so ties are broken by rule (Adam: *"When 2-8-4 is pasted, it should always face east.
     * Does it?"*): the plain copy over its turning twin, as a paste is no decision to reverse.  A compulsory turn has
     * no plain copy, so it still turns (Adam: *"for terminuses, they must reverse on paste"*).
     *
     * @param running the layout
     * @param locomotive the train
     * @param target the square being walked to
     * @param copies the target's copies, by name, in build order, which makes the last tie-break repeatable
     * @return the side of the nearest copy that can be driven to, or null when none can be
     */
    private Side walkTo(org.traincontrol.automation.Layout running, String locomotive,
        TileKey target, Map<String, Side> copies)
    {
        if (running == null || locomotive == null) return null;

        // Where the train IS, not every point of its locked path: an occupied square breaks the tie, else the first.
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
     * The walk itself, from a Point the caller names (OB-270), so a train Control+X took off can walk from its square.
     * @param copies the target's copies, by name, in the order the build made them
     * @return the side of the nearest copy that can be driven to, or null when none can be
     */
    private Side walkFrom(org.traincontrol.automation.Layout running, org.traincontrol.automation.Point from,
        Map<String, Side> copies)
    {
        // The whole walk, then the choice: breadth-first distances are true whatever order the shuffle gives.
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

                // Reached but not driven through: a route through a turning copy reverses mid-path, which the runtime
                // refuses (`reversesAlongTheWay`).
                if (next.isTerminus() || next.isReversing()) continue;

                queue.add(next);
            }
        }

        // The nearest copy; none reachable means no path, the caller's other arm.
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

            // Standing beats turned round, at the same distance.
            if (point != null && !point.isTerminus() && !point.isReversing()) return copy.getValue();

            // The first turning copy, in build order, in case that is all there is (a compulsory turn).
            if (turning == null) turning = copy.getValue();
        }

        return turning;
    }

    /**
     * Which way a train cut from one square would face on another if it drove there (OB-270), walking from the square
     * it was cut from.  Adam: *"it should be east.  no train should inadvertently change direction when pasted."*  Null
     * means no path, and the caller keeps the heading it was cut with (MT-368).
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
     * Both halves of the first sentence are one rule: the heading survives, so putting a train down must not turn it.
     *
     * A terminus holds one heading, so a train put down there takes it.
     *
     * The heading is read before the move and handed in: the landing copy's side is not the train's (see `landedOn`).
     *
     * @param held every copy the landing square builds to, by name, with the side each one faces
     * @param keep the heading the train had before it was moved, or null when that is not known
     * @param landedOn the copy the running layout chose - accepted and deliberately NOT used, because
     *        `StationIndex.speakerAt` says that on an empty square "any copy will do", so it is copy 0
     *        and evidence of nothing.
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

        // Several copies and the heading lost: the layout's pick was arbitrary, so nothing is recorded and the menu
        // asks.
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
     * The same, asking the running layout first.
     *
     * The setup names the square a train set off from until a capture writes the arrival back.
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

                // The copy it stands on IS its direction; the square's stored facing is the setup's last word about the
                // platform, not this train.
                Side onThisCopy = getStationIndex().facingsAt(where).get(point.getName());

                if (onThisCopy != null) return onThisCopy;

                // A copy with no facing in the index: the square's, right when there is one copy.
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
     * Records a facing and stands the train on the copy that faces that way (Adam: `flipFacing` updates the railway
     * too).
     *
     * The railway's train here first: after a run the setup names the pre-run occupant (behaviour.md 6a).  The setup's
     * record is written only where the railway has that train, or none.
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
     * The train standing on any copy of a square on the RAILWAY, or null; after a run the setup's need not be it.
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
     * Records the road the tail of the train here lies along (Adam: *"select the farthest sensor the tail of the train
     * recently crossed."*), as `Layout.namesOfRoad` writes it.
     * @param road [start, end] point-name pairs as a JSON array string, or null to forget
     */
    public void setArrivedAlong(TileKey tile, String road)
    {
        setPointProperty(tile, "arrivedAlong", road == null ? null : new org.json.JSONArray(road));
    }

    /** The recorded road as a JSON array string, or null. */
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
     * Which way the train on this square faces according to the RAILWAY: the copy it is on IS its direction.  The label
     * and the facing menu both read it (OB-181).  Null when no copy holds a train; the caller falls back to the setup.
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

            // On its way, the copy it will stand on (OB-314), or a train sent to turn reads backwards all journey.
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
     * Which way to draw a train no copy holds a facing for (OB-348): the setup's record where the track runs that way,
     * else the square's first facing, else its first way along the track; null with no track.  Adam: *"The icon can
     * guess in that case, but it must be N/S or a N/S track, or E/W on E/W, or angled on a curved track."*
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
     * The setup's recorded facing for the train here where the track runs that way, else null (OB-348); the record as
     * it stands where the track cannot be asked.  The icon, caption and Is Facing menu all read this.
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

    /** The sides a square's track runs to, in route order, no repeats; empty where it cannot be asked. */
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

    /** Records which way the locomotive on this square is pointing (`facing` null to forget). */
    public void setFacing(TileKey tile, Side facing)
    {
        setPointProperty(tile, AutonomyBuilder.FACING, facing == null ? null : facing.name());
    }

    /**
     * Gives a square a home locomotive, taking its home away from anywhere else: one locomotive, one station, as the
     * running layout enforces, or the next load drops one by iteration order.
     * @param tile the square to make home
     * @param locomotive the locomotive, or null to clear this square's home
     */
    public void setHome(TileKey tile, String locomotive)
    {
        writeHome(tile, locomotive);

        deriveStationIndex();
    }

    /**
     * The same, facing the way the operator said, for a train not standing here (OB-282; Adam: *"prompt the user for
     * the direction"*).  An impossible facing is not saved (*"we shouldn't allow an impossible facing to be saved"*).
     * @param tile the square to make home
     * @param locomotive the locomotive, or null to clear this square's home
     * @param facing the way it should face there, or null for the standing train's (`knownHomeFacing`)
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

    /** The ways a locomotive may be homed facing here (OB-282, `AutonomyBuilder.homeFacingsAt`); empty if not split. */
    public java.util.Set<Side> homeFacingsFor(TileKey tile)
    {
        return reducer == null ? java.util.Collections.<Side>emptySet() : builder(null).homeFacingsAt(tile);
    }

    /** `setHome` without re-deriving, for callers making several (`homeEveryPlacedTrain`). */
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

        // And its facing where it stands here, if a train may be brought home in it (OB-282)
        Side facing = knownHomeFacing(tile, locomotive);

        writePointProperty(tile, AutonomyBuilder.HOME_FACING, facing == null ? null : facing.name());
    }

    /** The facing a home here would be saved with without asking, or null when only the operator can say. */
    public Side knownHomeFacing(TileKey tile, String locomotive)
    {
        String facing = homeFacingOf(tile, locomotive);

        if (facing == null) return null;

        for (Side each : Side.values())
        {
            if (each.name().equals(facing)) return homeFacingsFor(tile).contains(each) ? each : null;
        }

        return null;
    }

    /** The way a locomotive faces on a square if it stands there (OB-282). */
    private String homeFacingOf(TileKey tile, String locomotive)
    {
        if (locomotive == null) return null;

        Object loc = getPointProperty(tile, "loc");

        String standing = loc instanceof org.json.JSONObject ? ((org.json.JSONObject) loc).optString("name", null) : null;

        // The railway first, as `facingOf` reads it: after a run the setup's facing is the way it set off (Adam:
        // *"Direction it is facing when home is set"*).
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

        // Elsewhere on the railway: the setup's facing is stale, so the editor asks.
        if (onTheRailway) return null;

        if (!locomotive.equals(standing)) return null;

        Object facing = getPointProperty(tile, AutonomyBuilder.FACING);

        return facing instanceof String ? (String) facing : null;
    }

    /**
     * Takes the home off every square, re-deriving once, through the single-square writer.
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

        // Once: the split names are computed from these properties.
        deriveStationIndex();

        return homed.size();
    }

    /**
     * Every station with a maximum train length above 0, and every square with a negative one, which `Layout.fromJSON`
     * refuses outright.  Adam: *"Add a right click menu open to clear all max station train lengths (grouped with the
     * other clear options)"*.
     * @return the squares, in no particular order
     */
    public java.util.List<TileKey> tilesWithAMaxTrainLength()
    {
        return tilesWhere((key, point) ->
        {
            if (!(point.opt("maxTrainLength") instanceof Number)) return false;

            int maximum = ((Number) point.opt("maxTrainLength")).intValue();

            // Stations only (OB-291), but a negative one anywhere: it still stops the railway loading.
            TileKey tile = AutonomyCompanionStore.parseTileKey(key);

            return maximum < 0 || (maximum != 0 && tile != null && store.isStation(tile));
        });
    }

    /**
     * Takes the maximum train length off every square, re-deriving once.
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
     * Every placement the bulk doors act on (excluded pages skipped), so a menu's count matches what the action does.
     * @return the placements, by square, as a copy
     */
    public Map<TileKey, String> placementsAutonomyWillWrite()
    {
        return new LinkedHashMap<>(placedLocomotives());
    }

    /**
     * Makes every placed train's square its home, re-deriving once (FR-075).  Adam: *"to bulk tools in the autonomy
     * editor, add an option to mass mark current train locations as their homes."*  Through `writeHome`, so one
     * locomotive, one station holds.  Where the railway has the train, that is where it is homed.  A square homed to
     * another train is overwritten, and counted.
     * @return how many squares were given a home
     */
    public int homeEveryPlacedTrain()
    {
        int assigned = 0;

        // Where the trains stand: the railway's, then the setup's
        for (Map.Entry<TileKey, String> placed : trainsWhereTheyStand().entrySet())
        {
            if (placed.getKey() == null || placed.getValue() == null) continue;

            writeHome(placed.getKey(), placed.getValue());

            assigned++;
        }

        // Once: the split names are computed from these properties.
        deriveStationIndex();

        return assigned;
    }

    /**
     * Every home the operator gave, square to locomotive, on pages autonomy runs (FR-115), for Place All Trains at
     * Their Homes.  A locomotive homed twice is offered once, on the square the loader keeps.
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
     * The square `Layout.rebuildHomeStations` keeps a twice-homed locomotive on: the first in HashMap order of the
     * built names.
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
     * Puts every homed locomotive on its home and takes every other off its station, in the SETUP only (FR-115; Adam:
     * "Place all at their homes.  Teleports locomotives to their home stations, facing the correct way, and clears all
     * other locomotives from other stations (without actually moving anything)").  Facing: the home's own, else the
     * caller's; no arrival side or road.  Settings travel with each train.  Every train the railway or setup has
     * standing is in the answer, as the rebuild puts back any it is not told about.  Excluded pages are left alone.
     * @param fallbackFacings for a home recorded without a facing, the way its locomotive should face there; may be
     *        empty
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

        // Off every station, settings kept for the home
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
     * Where this locomotive is already at home other than this square, for the menu's warning.
     * @param tile the square being assigned, which is not itself an answer
     * @param locomotive the locomotive
     * @return the other square that calls this locomotive home, or null
     */
    public TileKey homeElsewhere(TileKey tile, String locomotive)
    {
        java.util.List<TileKey> found = homesElsewhere(tile, locomotive);

        return found.isEmpty() ? null : found.get(0);
    }

    /** Every other square homed to that locomotive: a hand-edited setup can hold several. */
    private java.util.List<TileKey> homesElsewhere(TileKey tile, String locomotive)
    {
        if (locomotive == null) return new java.util.ArrayList<>();

        return tilesWhere((key, point) ->
            (tile == null || !tile.toString().equals(key))
                && locomotive.equals(point.optString("home", null)));
    }

    /** @return every square the active configuration homes a locomotive on, in no particular order; may be empty */
    public java.util.List<TileKey> tilesWithAHome()
    {
        return tilesWhere((key, point) -> carriesAHome(point));
    }

    /** @return every square the active configuration places a locomotive on (MT-257), in no particular order */
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
     * Sets one of a Point's operational properties in the active configuration, under the key parseAuto reads.
     * @param key terminus, reversing, active, maxTrainLength, speedMultiplier, priority, home, excludedLocs,
     *        excludedLocsMode
     * @param value the value, or null to remove the property entirely
     */
    public void setPointProperty(TileKey tile, String key, Object value)
    {
        writePointProperty(tile, key, value);

        // The split names are computed from these properties; this does not rebuild.
        deriveStationIndex();
    }

    /** The write alone, for a caller making several and re-deriving once. */
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

    /** @return the stored value, or null when this Point has no such property */
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
     * Sets one direction along the track from one square to another, found ignoring directions, so a one-way run can be
     * reversed by drawing it the other way.
     * @param from the square trains may leave
     * @param to the square they may travel toward
     * @return how many tiles were changed, or -1 if there is no continuous track between the two
     */
    public int setOneWayRun(TileKey from, TileKey to)
    {
        List<TileKey> path = graph.findUndirectedPath(from, to);

        if (path == null) return -1;

        Set<TileKey> changed = recordAlong(path, 1, path.size() - 2);

        // And the runs of the two squares clicked, if plain track (OB-361), or a run would say two things.
        TileKey first = path.get(0);
        TileKey last = path.get(path.size() - 1);

        changed.addAll(recordTheRunOf(first, graph.sideTowardNeighbour(first, path.get(1))));

        Route lastRoad = firstRoute(last);
        Side cameBy = graph.sideTowardNeighbour(last, path.get(path.size() - 2));

        if (lastRoad != null && cameBy != null) changed.addAll(recordTheRunOf(last, lastRoad.other(cameBy)));

        return directionsTouched() ? changed.size() : 0;
    }

    /** Records one way along the run of a plain-track square, without re-deriving (OB-361); returns the squares set. */
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

    /** Sets one direction along a known path, the track between its two ends. */
    private int applyOneWay(List<TileKey> path)
    {
        int changed = recordAlong(path, 1, path.size() - 2).size();

        return directionsTouched() ? changed : 0;
    }

    /**
     * Records one direction along a known path from `first` to `last` without re-deriving: a shortest-path search could
     * pick the other chain of a passing loop.  The rest are neighbours, for the path's way; an end square takes its way
     * from its one neighbour (OB-361).
     * @param path the squares in order
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

            // The path's own end, on a one-road square: the road's other side (not beside a link's jump).
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

            // On a switch, by its arms (OB-351)
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
     * Answers these squares 0 on purpose (OB-274), re-deriving once: Segment Length's clear (*"no, add a clear
     * button"*).  `setTileLength(tile, 0)` instead removes the answer.
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

    /** Writes several squares' lengths and re-derives once. */
    public void setTileLengths(java.util.Map<TileKey, Integer> lengths)
    {
        if (lengths == null || lengths.isEmpty()) return;

        for (java.util.Map.Entry<TileKey, Integer> square : lengths.entrySet())
        {
            store.setTileLength(square.getKey(), square.getValue() == null ? 0 : square.getValue());
        }

        touched();
    }

    /** Every square that has a length recorded (FR-069). */
    public java.util.List<TileKey> tilesWithALength()
    {
        return store.tilesWithALength();
    }

    /**
     * Forgets every length on every page, re-deriving once (FR-069).
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
     * Pairs two portals and switches both on (OB-031).  Adam: "when an active link from one page pairs with a link on
     * another, both must be marked as active. right now, the target doesn't have to be."
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
     * One arrow per run between two sensors, mid-run, so an unrestricted layout is not bare; facing runs collapse into
     * one double-headed arrow.
     * @return the square to mark, and what to draw there
     */
    public Map<TileKey, TileAnnotation.Mark> flowMarks()
    {
        return sessionRuns.flowMarks();
    }

    /** @return every run of plain track, by the tile that speaks for it (`TileGraph.runs`) */
    public Map<TileKey, Run> runs()
    {
        return sessionRuns.runs();
    }

    /** @return every tile of every run, mapped to the tile that speaks for it */
    public Map<TileKey, TileKey> runLeaders()
    {
        return sessionRuns.runLeaders();
    }

    /**
     * Whether a run can be made one way: a lone square, or a run with track between its ends (FR-112).
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
     * Sets a direction on every tile of a leader's run, in the leader's sense.
     * @param leader the tile the user set
     * @param routeId which of its routes
     * @param direction what they chose
     * @return how many tiles were changed
     */
    public int setRunDirection(TileKey leader, RouteId routeId, Direction direction)
    {
        return sessionRuns.setRunDirection(leader, routeId, direction);
    }

    /**
     * Whether a square follows its run (not its first square), and so draws no arrows of its own (OB-321, OB-361).
     * @return true for a square of a run that is not its first
     */
    public boolean followsItsRun(TileKey tile)
    {
        TileKey leader = tile == null ? null : leadersNow.get(tile);

        return leader != null && !leader.equals(tile);
    }

    /** @return the run's direction, as its first square's (`TileGraph.runsAs`), or null */
    private Direction runsAs(TileKey leader)
    {
        return graph == null ? null : graph.runsAs(leader);
    }

    /**
     * The direction a square's road is drawn with: the run's for its first square, else what is stored (OB-361).
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
     * Which way each route across a square runs, as marks (FR-037), shared by the editor and the track diagram.  The
     * editor's own reasons for silence are not shared; the hardware correction is.
     * @return one mark per route, never null, empty when the square has no routes or no graph
     */
    public List<TileAnnotation.Mark> directionMarks(TileKey tile)
    {
        List<TileAnnotation.Mark> marks = new ArrayList<>();

        // None on stationless track (OB-353)
        if (graph == null || tile == null || reachesNoStation(tile)) return marks;

        for (Map.Entry<TileGraph.RouteId, TilePorts.Route> entry : getRoutes(tile).entrySet())
        {
            TilePorts.Route route = entry.getValue();

            TileGraph.Direction direction = graph.getDirection(tile, entry.getKey());

            // The blades make a route one-way whatever was authored; a shut route draws nothing (on a permanent
            // turnout `TOWARD_A` is a closure).
            if (!TileGraph.isPassable(direction, route))
            {
                direction = TileGraph.Direction.NONE;
            }
            else if (route.getDirectedToward() != null)
            {
                direction = route.getDirectedToward() == route.getA()
                    ? TileGraph.Direction.TOWARD_A : TileGraph.Direction.TOWARD_B;
            }

            // A run's first square draws the run (OB-361)
            TileGraph.Direction run = runsAs(tile);

            if (run != null) direction = run;

            marks.add(new TileAnnotation.Mark(route.getA(), route.getB(), direction));
        }

        return marks;
    }

    /**
     * What the track diagram draws on each square, in one pass (OB-321, FR-110): a following square draws none, a run's
     * first square draws the run.  Restrictions and allowances are chosen apart (MT-675; Adam: *"allow the user to
     * choose: restrictions, allowances, or both.  don't make them mutually exclusive."*).
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
     * What the setup says about one square for the track diagram, restriction arrows (FR-037) on plain track too.
     * @return the annotation, or null when this square has nothing to say
     */
    public TileAnnotation staticAnnotationFor(TileKey tile)
    {
        return staticAnnotationFor(tile, org.traincontrol.gui.TrainControlUI.diagramShowsRestrictionArrows());
    }

    /**
     * The same, with the arrows option read once by a caller describing many squares (it reads Windows settings).
     * @param arrows whether the one-way arrows are drawn, as diagramShowsRestrictionArrows() answered for this pass
     * @return the annotation, or null when this square has nothing to say
     */
    public TileAnnotation staticAnnotationFor(TileKey tile, boolean arrows)
    {
        return staticAnnotationFor(tile, arrows, arrows);
    }

    /**
     * The same, with the ingress arrows asked apart from the direction arrows (MT-675).
     * @param tile the square
     * @param arrows whether the direction arrows are drawn - red, green or both, as the annotation is then told
     * @param arrivals whether the station ingress arrows are drawn
     * @return the annotation, or null when this square has nothing to say
     */
    public TileAnnotation staticAnnotationFor(TileKey tile, boolean arrows, boolean arrivals)
    {
        return withoutTheCrossingsMiddle(tile, staticAnnotationOf(tile, arrows, arrivals));
    }

    /**
     * Leaves out the arrows where a two-square crossing's squares meet (OB-322; Adam: *"the crossings consisting of 2
     * fixed Y's or 2 sets of static switches have a redundant set of (red/green) arrows in the middle.  let's hide them
     * in that configuration for simplicity."*).
     * @return the same annotation
     */
    public TileAnnotation withoutTheCrossingsMiddle(TileKey tile, TileAnnotation annotation)
    {
        TileKey partner = graph == null || annotation == null ? null : graph.crossingPartner(tile);

        if (partner != null) annotation.withoutArrowsAt(graph.sideTowardNeighbour(tile, partner));

        return annotation;
    }

    /** What `staticAnnotationFor` answers, before a crossing's middle is left out. */
    private TileAnnotation staticAnnotationOf(TileKey tile, boolean arrows, boolean arrivals)
    {
        if (graph == null || reducer == null) return null;

        // A disabled link is greyed (its art does not show it), before the Point test: a link may lack a sensor.
        if (isDisabledLink(tile))
        {
            return new TileAnnotation(new ArrayList<TileAnnotation.Mark>(), -1, false, null, true);
        }

        // The one-way arrows (FR-037), before the Point test: restrictions are about track, not sensors.
        List<TileAnnotation.Mark> marks = arrows ? directionMarks(tile)
            : new ArrayList<TileAnnotation.Mark>();

        // Restricted track that is not a Point; null when there is nothing to say.
        if (!reducer.getPoints().containsKey(tile))
        {
            return marks.isEmpty() ? null
                : new TileAnnotation(marks, -1, false, null, false, isCurveSquare(tile), false, null, arrows, null);
        }

        String name = store.getPointName(tile);

        // A plain point draws no badge: its art says it.  A shut square still draws its cross, as in the editor.
        boolean shut = Boolean.FALSE.equals(getPointProperty(tile, "active"));

        boolean worthABadge = store.isStation(tile) || isTurnAround(tile) || shut;

        // The marks go over whole with `blockedOnly` saying which to draw, so the two drawings cannot disagree.
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
                // which way a station's track runs out, for a terminus's flat end (FR-118)
                store.isStation(tile) ? deadEndSide(tile) : null),
            false, isCurveSquare(tile), false, null,
            // Restricted only: open both ways is the default and nearly every square
            arrows,
            // Ingress arrows follow the same switch; `false` means only where something is restricted.
            arrivals ? arrivalMarks(tile, false)
                : new ArrayList<TileAnnotation.Arrival>());
    }

    /**
     * The side of a square's road whose drawn track runs out before another sensor, or null (FR-118): a station there
     * is drawn as a terminus against it (Adam: *"a station with only one way out becomes a terminus, correct?"* -
     * confirmed).  Asked of the track as drawn, not the built edges.
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
     * Stations whose every way in is barred; one with no arrival sides never splits, so is not shut.
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
     * A station's arrival marks, or none where there is nothing to say.
     * @param always true to mark every arrival side of every station (the editor's view), false to mark only the
     *        stations that restrict something (the running diagram)
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
     * Whether this square is a link autonomy leaves alone: still a link, and switched off (a stale setting is unseen).
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
     * The road a Point's badge is drawn on: its sensor's contact road (`TilePorts.contactRoad`, MT-712), else the
     * first.
     * @return the road, or null where the square has none in the graph
     */
    public Route stationRoad(TileKey tile)
    {
        org.traincontrol.base.LayoutDiagramComponent square = graph == null ? null : graph.getTiles().get(tile);

        if (square != null && square.isFeedback())
        {
            Route contact = TilePorts.contactRoad(square.getType(), square.getOrientation());

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
     * Whether a square is one of the four curved types, whose arrows lie along their chords (MT-712).
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

    /** The tile's first route, where a non-sensor's badge is drawn. */
    private Route firstRoute(TileKey tile)
    {
        Map<RouteId, Route> routes = graph == null ? null : graph.getRoutes(tile);

        return routes == null || routes.isEmpty() ? null : routes.values().iterator().next();
    }

    /** Every route of a tile, so the panel can offer a switch's branches individually. */
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
     * Whether this session can safely judge what the diagram no longer has: every page loaded (an unhydrated OneDrive
     * placeholder is ordinary) and the numbering not mid-renumber (OB-068: save, but do not prune).  Every pruner here
     * must ask it.
     * @return true when everything the setup knows about is here and correctly numbered
     */
    public boolean pagesSafeToJudge()
    {
        java.util.Set<String> loadedNames = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            // A stand-in for an unreadable page is not loaded: it carries the real name, and a save would prune the
            // whole page against its one text tile (MT-135).
            if (page.isUnreadable()) continue;

            loadedNames.add(page.getName());
        }

        return !store.isPageNumberingSuspect() && store.pagesNotLoaded(loadedNames).isEmpty();
    }

    /**
     * Writes the setup out and forgets what the diagram no longer has, unless it cannot tell (`pagesSafeToJudge`): then
     * nothing is pruned and the Reconciliation reports a decline.
     * @return what reconciling found, for showing
     * @throws IOException
     */
    public AutonomyCompanionStore.Reconciliation save() throws IOException
    {
        // Every tile of every page, excluded ones too: excluding must be reversible.
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

        // Not while the numbering is suspect or a known page is missing (OB-068): its every entry would read as deleted
        // and be pruned.  Saving still happens; only the deleting waits.
        java.util.Set<String> loadedNames = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            // The same skip as `pagesSafeToJudge`: here too, or `absent` is empty and the decline silent.
            if (page.isUnreadable()) continue;

            loadedNames.add(page.getName());
        }

        java.util.List<String> absent = store.pagesNotLoaded(loadedNames);

        boolean incomplete = !pagesSafeToJudge();

        // Callers are told through the Reconciliation; nothing is destroyed while it is non-empty.

        AutonomyCompanionStore.Reconciliation report = incomplete
            ? AutonomyCompanionStore.Reconciliation.declined(absent)
            : store.reconcile(existing);

        if (!incomplete) forgetArrivalsThatNoLongerExist();

        store.save();

        dirty = false;

        rebuild();

        return report;
    }

    /**
     * Writes the setup out without reconciling, for paths saving while the diagram is replaced: the pages may hold
     * edits the user cancelled, and reconciling would delete what those squares had.
     * @throws IOException
     */
    public void saveWithoutReconciling() throws IOException
    {
        store.save();

        dirty = false;
    }
    /**
     * How to look a train's length up (FR-046): a function, as the length lives on the Locomotive.  Null until
     * installed, and the checks using it are silent meanwhile.
     */
    private java.util.function.Function<String, Integer> trainLengths;

    /** @param lengths a function from locomotive name to length, or null to stop asking */
    public void setTrainLengthSource(java.util.function.Function<String, Integer> lengths)
    {
        this.trainLengths = lengths;
    }

    /**
     * Who stands where: the railway's trains, then the setup's placements of trains it lacks on squares it leaves free.
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
     * Squares holding a train with no length set; empty with no source installed (unknown is not unset).
     * @return the squares and the trains standing on them, where the railway has them first
     */
    private Map<TileKey, String> placedTrainsWithoutLength()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();

        if (trainLengths == null) return out;

        for (Map.Entry<TileKey, String> placed : trainsWhereTheyStand().entrySet())
        {
            if (placed.getValue() == null || placed.getValue().trim().isEmpty()) continue;

            Integer length = trainLengths.apply(placed.getValue());

            // Zero is unset to the railway: never too long.
            if (length == null || length <= 0) out.put(placed.getKey(), placed.getValue());
        }

        return out;
    }

    /**
     * Whether this railway models lengths at all (a measured tile, a station maximum, a train length): with none,
     * length notices would list things that are not wrong.
     * @return true when anything on this railway carries a length
     */
    private boolean modelsAnyLength()
    {
        if (store != null && store.measuresAnyTrack()) return true;

        if (reducer != null && store != null)
        {
            for (TileKey square : reducer.getPoints().keySet())
            {
                // A station's only (OB-291).
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
     * Stations taking a train of any length (FR-046), gated on the railway modelling any length, not track lengths:
     * `maxTrainLength` compares only with the train's own length.
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

    /** Whether any length-taking square of a leg has a length, which makes the berth rule judge it (OB-273). */
    private boolean anythingMeasuredOn(java.util.List<TileKey> squares)
    {
        // A leg answered 0 throughout is measured, at no length (`Edge.isMeasured`).
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
     * The track a parking berth's rule spends walking back from the berth over one leg until it stops (OB-288), as
     * `Layout.whyABerthCannotHoldIt` spends it, berth first.
     * @param squares the leg's squares in order, the berth last
     * @return {measured length before the stop, squares before it with no length, how it stops}: 1 where the berth
     *         rule refuses (another road runs over), 2 where only the room walk ends, 0 at none
     */
    private int[] berthTrackBeforeTheStop(java.util.List<TileKey> squares)
    {
        int held = 0;
        int unmeasured = 0;

        TileKey berth = squares.isEmpty() ? null : squares.get(squares.size() - 1);

        for (int at = squares.size() - 1; at >= 0; at--)
        {
            TileKey tile = squares.get(at);

            if (getGraph() != null && takesNoLength(tile)) continue;

            if (getGraph() != null && berth != null && endsTheBerthsRoom(tile, berth))
            {
                return new int[] {held, unmeasured, anotherRoadRunsOver(tile, berth) ? 1 : 2};
            }

            int length = store.getTileLength(tile);

            held += Math.max(0, length);

            // An answered 0 is not missing (Adam: "stop listing answered zeros as missing").
            if (length <= 0 && !store.isTileLengthAnswered(tile)) unmeasured++;
        }

        return new int[] {held, unmeasured, 0};
    }

    /**
     * Parking berths whose approach is measured in part, the state that refuses trains that fit (Adam: *"We want clear
     * warnings to the user"*): one measured square makes `whyABerthCannotHoldIt` judge, and unmeasured squares hold
     * nothing.  Asked per arriving leg, against the station's longest train (OB-288, MT-552).  The ordinary state
     * between Mass Assign Lengths sittings.
     * @return the station squares, each mapped to how many squares of its approach before the stop still have no length
     */
    public java.util.Map<TileKey, Integer> stationsWithAHalfMeasuredApproach()
    {
        java.util.Map<TileKey, Integer> out = new LinkedHashMap<>();

        if (reducer == null || store == null) return out;

        for (TileKey square : reducer.getPoints().keySet())
        {
            if (!store.isStation(square)) continue;

            // Stations autonomy chooses are exempt from the rule, so not warned about.
            if (isAutoDestination(square)) continue;

            // The longest train it takes, if set (OB-288).
            Object stated = getPointProperty(square, "maxTrainLength");

            int longest = stated instanceof Number ? ((Number) stated).intValue() : 0;

            // One approach at a time: the walk judges the approach a train arrives on.
            int worst = 0;

            for (GraphReducer.ReducedEdge arriving : reducer.getEdges())
            {
                if (!arriving.getEnd().equals(square)) continue;

                // Not a side no train stops from (MT-552).
                if (getBarredArrivals(square).contains(arriving.getEntrySide())) continue;

                boolean anyMeasured = false;

                // The path and the berth itself, which the walk spends first (OB-278).
                java.util.List<TileKey> squares = new java.util.ArrayList<>();

                for (GraphReducer.TileStep step : arriving.getPath()) squares.add(step.getTile());

                squares.add(square);

                // Measured anywhere on the leg makes the rule judge it, as for the run-in notice.
                anyMeasured = anythingMeasuredOn(squares);

                if (!anyMeasured) continue;

                // Not where the measured track before the stop holds its longest train (OB-288, MT-552; Adam, of
                // BottomMainPost: *"trains of length 3 can hold there"*); only holes before the stop count.
                int[] before = berthTrackBeforeTheStop(squares);

                if (longest > 0 && before[0] >= longest) continue;

                if (before[1] > worst) worst = before[1];
            }

            if (worst > 0) out.put(square, worst);
        }

        return out;
    }

    /**
     * Stations told they hold more train than their run-in measures.  Adam: *"Let's add an autonomy editor notice that
     * alerts the user if a run-in is shorter than the berth length, that way they can decide if it makes sense or not.
     * for example, a station of length 4 may have two segments of tracks on either side of a switch, of length 2.  that
     * is acceptable and its limitations are understood."*  The room is the guard's own number (`getRoomAtTheEnd`), or
     * for a parking berth the berth rule's where it refuses; at a platform, the refusing figure where there is one
     * (FR-087; Adam: *"Add the refusing figure where there is one."*).  An edge crossing no switch counts only where
     * the train turns at its far end.  Silent without a maximum or with no track measured.
     * @return the station squares, each mapped to {the stated maximum, the smallest measured room} - and at a platform
     *         with a refusing figure under its maximum, that figure third
     */
    java.util.Map<TileKey, int[]> runInsShorterThanTheBerth()
    {
        return runInFigures(null, builtForInspection(), null);
    }

    /**
     * The walk behind `runInsShorterThanTheBerth`, collecting too the parking berths given no room by a way in answered
     * 0 throughout (Adam: *"Give it its own sentence as a warning, make it sound intuitive (the effective specified
     * length of the track is 0)"*).
     * @param givenNoRoom where to put the berths given no room, or null when nobody asks
     * @param built the railway the setup builds, for the refusing figures, or null when it will not build
     * @param named the built railway's point names to squares, or null to ask the builder
     * @return what `runInsShorterThanTheBerth` returns
     */
    private java.util.Map<TileKey, int[]> runInFigures(java.util.Set<TileKey> givenNoRoom, org.json.JSONObject built,
        java.util.Map<String, TileKey> named)
    {
        java.util.Map<TileKey, int[]> out = new LinkedHashMap<>();

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

                // NOT A SIDE NO TRAIN STOPS FROM (MT-552), as in the half-measured notice
                if (getBarredArrivals(square).contains(arriving.getEntrySide())) continue;

                int room = arriving.getRoomAtTheEnd();

                // A PARKING BERTH'S ROOM ENDS WHERE THE BERTH RULE STOPS: at a crossing as well as a switch.  The room
                // walk runs on past a crossing (`boundsTheRoom`), so quoting it would give room the berth rule does not
                // give.  A platform autonomy may choose is not judged by the berth rule and keeps the room walk's
                // number.
                //
                // AND WITH NOTHING SPENT BEFORE THE STOP, every train is refused there (once anything on the approach
                // is measured, and only where the stop is one the berth rule refuses on: another road runs over it), so
                // the room walk's figure is not quoted either.  Squares nobody has answered are the half-measured
                // notice's to name; answered 0 on purpose, the berth's own warning's.
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

                        // NOTHING BEFORE THE STOP, ANSWERED SO: the berth takes no train that comes in this
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

                // UNMEASURED IS UNKNOWN, NOT SHORT, the doctrine the guard follows: `-1` is the reduction's word for
                // "bounded, and nothing in it is measured".  Not the berth rule's 0, which is what it refuses on.
                if ((room <= 0 && !theBerthRulesFigure) || room >= max) continue;

                if (worst < 0 || room < worst) worst = room;
            }

            // AND AT A PLATFORM, THE SHORTEST WAY IN: a train longer than that, setting off from the station or
            // turn nearest behind, is refused where the notice says it stands across the switch.
            Integer refusedAbove = isAutoDestination(square) ? refusing.get(square) : null;

            if (worst >= 0)
            {
                // NEVER UNDER {3}, since both are taken over the same ways in, and given where it EQUALS {3}: the
                // shortest way in holds nothing behind its switch, and there a train longer than {3} is refused instead
                // of standing across it.
                out.put(square, refusedAbove != null && refusedAbove > 0 && refusedAbove < max
                    ? new int[] {max, worst, refusedAbove} : new int[] {max, worst});
            }
        }

        return out;
    }

    /**
     * The figure a train is refused above at each platform, read off the railway the setup builds: the least, over the
     * notice's own ways in, of what `Layout.whyTooLongForThisRoute` refuses above on each (the larger of the room past
     * the switch and the measured route in, `Layout.measuredRouteIn`, FR-087).  Off the built railway, not the squares:
     * a square's copies are its directions, and nobody is started at a copy trains may not arrive at.  Only the
     * notice's own ways in (`roomTheNoticeQuotes`, so never less than `{3}`), walked back as the railway runs it
     * (`routesIn`).
     *
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

            // SWITCHED OFF, NOTHING PASSES OR ARRIVES: `isPathClear` refuses a route through a point that is not
            // active, or into one, but not out of one (Adam: *"Just not inactive start points.  Inactive really means
            // nothing can pass."*).
            if (!point.optBoolean("active", true)) off.add(name);

            if (point.optBoolean("station", false))
            {
                stops.add(name);
                setsOff.add(name);
            }

            // THE ROUTE IN STOPS AT A TURN: `measuredRouteIn` counts nothing behind a copy the train turned at (a
            // barred turning copy, which is no station).
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

                // No train comes in this way.  A leg with no length never gets here: it quotes no room.
                if (walked == null) continue;

                out.merge(platform.getKey(), Math.max(roomTheNoticeQuotes(edge, turns), walked), Math::min);
            }
        }

        return out;
    }

    /**
     * The room `{3}` is taken over for a leg into a platform, or none where that notice does not read the leg: past a
     * switch, what is measured there (-1 where nothing is); straight from a copy trains turn at, the leg's length;
     * otherwise 0, as the room walk keeps walking back and this leg alone bounds nothing.
     *
     * @return the room, 0 or less where it gives none
     */
    private static int roomTheNoticeQuotes(org.json.JSONObject leg, java.util.Set<String> turns)
    {
        if (leg.has("roomAtTheEnd")) return leg.optInt("roomAtTheEnd", -1);

        return turns.contains(leg.getString("start")) ? leg.optInt("length", 0) : 0;
    }

    /**
     * Whether a built leg was answered 0 on purpose at every place: measured track of no length, as `Edge.isMeasured`
     * reads it on the railway.
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
     * The least the route in counts for a train coming along each built leg towards this platform
     * (`Layout.measuredRouteIn` over every way the railway can run it).  Worked out for every leg together and lowered
     * until nothing changes, so a loop of sensors is walked whole: one leg at a time and remembered, a leg first
     * reached inside a loop would be stored as having no way in.  From a copy a train is started at or turns at, the
     * leg alone: the route in stops there.  A leg nobody measured ends the route in, so nothing behind it counts: 0;
     * one answered 0 throughout is measured track of no length (`answeredThroughout`) and is passed like any other.
     * Otherwise the leg and the least way into where it starts.  Only from where a train can be got to without passing
     * the platform, and never from or through another copy of its own square: a route from a platform round to itself
     * is no journey.
     *
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
     * Stations every train must turn round at, reached from more than one side (MT-361).  Adam: *"terminuses (stations
     * that must reverse) are currently allowed to have ingress from two sides.  Make this be an autonomy ERROR that the
     * user has to fix."*  Three conditions, each keeping the rule off a railway that is correct: **must turn, not may**
     * (a may-turn square with two ways in is the ordinary station a train can run through or reverse in); **a station**
     * (a must-turn square that is not one is a plain reversing point, a mid-layout turn-round); **unbarred sides**
     * (shutting one side is one of the three remedies the message names, so a barred side must not go on being
     * counted).  Counted as SIDES, not arriving edges, as two edges can reach one square by the same side (an arrival
     * through a portal arrives by a real side).
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
     * The sensor addresses that sensor squares on two or more places share, each to its squares in reading order
     * (FR-139).  A place is a square, counted once: the squares are read off the diagram's pages and not the copies
     * they build into.  A page left out of autonomy has no places, and an address of zero or less is no address, as
     * `repeatedSensorPages` and the reduction both read it.  Nor has a page `repeatedSensorPages` reports: it repeats
     * another page's sensors, an ERROR already (OB-150), and the same thing is not said twice (Adam: "suppress the
     * warning where OB-150 applies"); this one is about squares that share an address on a page of their own.
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
     * Included pages that repeat an s88 another included page already uses (OB-150): the same walk
     * `excludeRepeatedSensorPages` does, so two spellings of "repeats a sensor" cannot disagree about which page was at
     * fault.  Earliest page wins, as there: the first page to use a sensor keeps it and a later one repeating it is
     * named, so the error names the page the automatic answer would have shut.
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
                    // One per repeated sensor: three repeats on one page are three things to fix, and one line would
                    // hide that fixing two changes nothing about the message.  The square on the REPEATING page is the
                    // one to do something about; the page that had the address first is innocent and is named in the
                    // message.
                    repeats.put(new TileKey(page.getName(), component.getX(), component.getY()),
                        I18n.f("autosetup.ui.duplicateSensorSubject",
                            sensor, page.getName(), seen.get(sensor)));

                    // AND THE PAGE STOPS COUNTING, as `excludeRepeatedSensorPages` does (MT-223): a repeating page is
                    // shut there and contributes nothing further, so a third page sharing one of its other sensors is
                    // not blamed against it (an ERROR that would refuse the whole setup).  FLAGGED, NOT BROKEN OUT OF:
                    // stopping the scan would cost the rest of the page (Adam's page 3 is a zoomed view of page 1 and
                    // clashes on many sensors, each reported).
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
