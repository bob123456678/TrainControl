package ui;

import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.Route;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.LayoutPopupUI;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomy.DiagramMonitorDriver;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.Reflect;
import support.Settle;
import support.StandInStation;
import support.TestStationAddress;
import support.WindowField;

/**
 * The doors that change what the track diagram does not draw keep the diagram and every cached page: following a link,
 * and a route renumbered, disarmed or deleted, while the route squares follow in place; a locomotive renamed is renamed
 * on every caption, a page window's too; and a sync that reads the pages again, as Sync of one locomotive does from a
 * local folder, still redraws an edit made elsewhere (the owner's rule: renames and remote diagram changes must still
 * update).  A page window closes once its page is gone, follows its page through a rename, and keeps its size through
 * a page edit, and is drawn again only when its page changed; Pop Up All does the windows' shared work once.  On a
 * copy of the frozen railway, syncing with a stand-in station.
 */
public class testTheDiagramIsKeptForWhatItDoesNotShow
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static StandInStation station;
    private static String addressWas;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        AskingWindows.Railway opened = AskingWindows.openRailway("live-snapshot", null);

        sandbox = opened.sandbox;
        model = opened.model;
        ui = opened.ui;

        // A Central Station 2 with no routes file: a sync reads the pages from the folder, then fails before the routes
        station = new StandInStation((path, n) -> "/config/geraet.vrs".equals(path)
            ? "[geraet]\ngeraet\n .produkt=Central Station 2\n".getBytes(StandardCharsets.UTF_8) : null);

        addressWas = TestStationAddress.get();

        TestStationAddress.set(station.address());

        Components.settle();

        session = ui.getAutonomySession();

        assertNotNull(session, "no autonomy setup on this railway");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        TestStationAddress.set(addressWas);

        // The layout preference first, which nothing below can stop
        if (sandbox != null) sandbox.close();
        if (station != null) station.close();
    }

    @BeforeMethod
    public void showTheMainPage() throws Exception
    {
        Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), PAGE);

        Settle.theDiagram(ui);
    }

    /**
     * A link square shows its page from the cache, as picking the page does, and the page it left stays cached (no
     * second, fresh drawing that empties the cache of every other page).
     *
     * MUTATION: put back `this.repaintLayout()` after the selection in `TrainControlUI.followLink`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testFollowingALinkShowsItsPageFromTheCache() throws Exception
    {
        final LayoutDiagramComponent link = Components.onTheEventThread(() ->
        {
            for (LayoutLabel square : Components.all(shownPage(), LayoutLabel.class, each -> each.getComponent() != null
                && each.getComponent().isLink() && each.getComponent().getLinkPageName() != null
                && !PAGE.equals(each.getComponent().getLinkPageName())))
            {
                return square.getComponent();
            }

            return null;
        });

        assertNotNull(link, "precondition: " + PAGE + " has no link square to another page");

        final String target = link.getLinkPageName();

        // Both pages drawn once, so both are cached
        Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), target);
        Settle.theDiagram(ui);
        Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), PAGE);
        Settle.theDiagram(ui);

        final Component left = Components.onTheEventThread(() -> shownPage());
        final JPanel cachedTarget = Components.onTheEventThread(() -> ui.layoutCache.get(cacheKey(target)));

        assertNotNull(cachedTarget, "precondition: " + target + " is not cached after being shown");

        SwingUtilities.invokeAndWait(() -> ui.followLink(link));

        Settle.theDiagram(ui);

        assertEquals(Components.onTheEventThread(() -> String.valueOf(((JComboBox<?>) WindowField.of(ui, "LayoutList"))
            .getSelectedItem())), target, "the link did not go to " + target);

        assertSame(Components.onTheEventThread(() -> shownPage()), cachedTarget,
            "the link drew " + target + " afresh instead"
            + " of showing it from the cache");

        assertSame(Components.onTheEventThread(() -> ui.layoutCache.get(cacheKey(PAGE))), left,
            "following the link threw away"
            + " the cached " + PAGE);
    }

    /**
     * A route renumbered moves off its square in place: the square no longer names it nor starts it, and the diagram
     * and the cached pages stay; renumbered back with a sync that reads the pages again, the diagram is drawn afresh
     * and the square names and starts it again.
     *
     * MUTATION: leave out `grid.rebindRouteSquares()` in `TrainControlUI.redrawRouteSquaresOrRebuild`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testARenumberedRouteLeavesItsSquareWithoutARebuild() throws Exception
    {
        final LayoutDiagramComponent square = aBoundRouteSquare();
        final MarklinRoute route = (MarklinRoute) square.getRoute();
        final int id = route.getId();
        final boolean wasLocked = route.isLocked();

        // A route made here, so the station is not asked
        int highest = 999;

        for (String each : model.getRouteList()) highest = Math.max(highest, model.getRouteId(each));

        final int free = highest + 1;

        route.setLocked(false);

        try
        {
            final Kept kept = keptNow();

            try (Answering answering = Answering.start(message -> Answering.type(Integer.toString(free))))
            {
                SwingUtilities.invokeAndWait(() -> ui.changeRouteId(route.getName()));
            }

            Settle.theDiagram(ui);

            assertEquals(route.getId(), free, "precondition: the route was not renumbered");

            kept.assertKept("renumbering " + route.getName());

            assertNull(square.getRoute(), "precondition: the model left the square on the renumbered route");

            assertFalse(String.valueOf(tipOf(square)).contains(route.getName()), "the square of route " + id
                + " still says " + tipOf(square) + " after the route became " + free);

            // Back, which the station could have issued, so it syncs, reading the pages again
            try (Answering answering = Answering.start(message -> Answering.type(Integer.toString(id))))
            {
                SwingUtilities.invokeAndWait(() -> ui.changeRouteId(route.getName()));
            }

            Settle.theDiagram(ui);

            assertEquals(route.getId(), id, "precondition: the route was not renumbered back");

            assertNotSame(Components.onTheEventThread(() -> shownPage()), kept.shown,
                "a sync that read the pages again did"
                + " not draw the diagram afresh");

            LayoutLabel now = labelOf(squareNow(square));
            String says = Components.onTheEventThread(() -> now.getToolTipText());

            assertTrue(String.valueOf(says).contains(route.getName()), "renumbered back, the square says " + says);

            assertTrue(tilesOf(route).contains(now), "renumbered back, the square does not show the route running");
        }
        finally
        {
            route.setLocked(wasLocked);

            if (route.getId() != id) model.changeRouteId(route.getName(), id);
        }
    }

    /**
     * Disarming a route made here writes it anew, and its square starts the new one and shows it running, with the
     * diagram and the cached pages kept.
     *
     * MUTATION: leave out `grid.rebindRouteSquares()` in `TrainControlUI.redrawRouteSquaresOrRebuild`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testADisarmedRouteKeepsItsSquareWithoutARebuild() throws Exception
    {
        final LayoutDiagramComponent square = aBoundRouteSquare();
        final MarklinRoute route = (MarklinRoute) square.getRoute();
        final boolean wasLocked = route.isLocked();
        final boolean wasEnabled = route.isEnabled();

        route.setLocked(false);

        try
        {
            final Kept kept = keptNow();
            final LayoutLabel label = labelOf(square);

            disarm(route);

            kept.assertKept("disarming " + route.getName());

            Route written = model.getRoute(route.getName());

            assertSame(square.getRoute(), written, "precondition: the model left the square on the old route");

            assertTrue(tilesOf((MarklinRoute) written).contains(label), "the square of " + route.getName()
                + " does not show the route written anew running");
        }
        finally
        {
            putBack(route.getName(), wasLocked, wasEnabled);
        }
    }

    /**
     * A page window's route square follows a route written anew as the main window's does, the window kept through an
     * editor's Cancel (FR-216): disarming a route made here, the window's square shows the new route running, without a
     * redraw of the window.
     *
     * MUTATION: leave the page windows out of the grids `TrainControlUI.redrawRouteSquaresOrRebuild` binds again, and
     * this fails.
     */
    @Test(timeOut = 120000)
    public void testAPageWindowsRouteSquareFollowsARouteWrittenAnew() throws Exception
    {
        final LayoutDiagramComponent square = aBoundRouteSquare();
        final MarklinRoute route = (MarklinRoute) square.getRoute();
        final boolean wasLocked = route.isLocked();
        final boolean wasEnabled = route.isEnabled();
        final LayoutPopupUI window = aKeptWindow();

        route.setLocked(false);

        try
        {
            final LayoutLabel inWindow = squareIn(window, square);

            disarm(route);

            Route written = model.getRoute(route.getName());

            assertTrue(Components.onTheEventThread(() -> SwingUtilities.isDescendingFrom(inWindow, window)),
                "precondition: disarming " + route.getName() + " redrew the page window");

            assertTrue(tilesOf((MarklinRoute) written).contains(inWindow), "the page window's square of "
                + route.getName() + " does not show the route written anew running");
        }
        finally
        {
            SwingUtilities.invokeAndWait(window::dispose);

            putBack(route.getName(), wasLocked, wasEnabled);
        }
    }

    /**
     * A route square on a page kept in the cache stays its route's while another page is shown and the route runs, so
     * shown again from the cache it shows the route's state (OB-463).
     *
     * MUTATION: in `MarklinRoute.updateTiles`, also drop a label that is not displayable, as a cached page's are, and
     * this fails.
     */
    @Test(timeOut = 120000)
    public void testACachedPagesRouteSquareStaysItsRoutesThroughARun() throws Exception
    {
        final LayoutDiagramComponent square = aBoundRouteSquare();
        final MarklinRoute route = (MarklinRoute) square.getRoute();
        final LayoutLabel label = labelOf(square);

        Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), "2 - Bottom");
        Settle.theDiagram(ui);

        assertFalse(Components.onTheEventThread(label::isDisplayable), "precondition: " + PAGE + " is still on screen");

        // What a run does as it starts and as it ends
        route.updateTiles();

        Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), PAGE);
        Settle.theDiagram(ui);

        assertSame(labelOf(square), label, "precondition: " + PAGE + " was not shown from the cache");

        assertTrue(tilesOf(route).contains(label), "the route square of " + route.getName() + " on " + PAGE
            + ", kept in the cache while another page was shown, no longer shows the route running after it ran");
    }

    /**
     * Deleting a route made here takes it off its square, which no longer names it, with the diagram and the cached
     * pages kept.
     *
     * MUTATION: leave out `grid.rebindRouteSquares()` in `TrainControlUI.redrawRouteSquaresOrRebuild`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testADeletedRouteLeavesItsSquareWithoutARebuild() throws Exception
    {
        final LayoutDiagramComponent square = aBoundRouteSquare();
        final MarklinRoute route = (MarklinRoute) square.getRoute();
        final String name = route.getName();
        final int id = route.getId();
        final boolean wasLocked = route.isLocked();

        route.setLocked(false);

        try
        {
            final Kept kept = keptNow();

            try (Answering answering = Answering.start(message -> Answering.press(AskingWindows.YES)))
            {
                SwingUtilities.invokeAndWait(() -> ui.deleteRoute(name));
            }

            Settle.theDiagram(ui);

            assertNull(model.getRoute(name), "precondition: the route was not deleted");

            kept.assertKept("deleting " + name);

            assertFalse(String.valueOf(tipOf(square)).contains(name), "the square of the deleted route " + name
                + " still says " + tipOf(square));
        }
        finally
        {
            if (model.getRoute(name) == null)
            {
                model.newRoute(name, id, route.getRoute(), route.getS88(), route.getTriggerType(), route.isEnabled(),
                    route.getConditions());
            }

            ((MarklinRoute) model.getRoute(name)).setLocked(wasLocked);

            rebuildThePage();
        }
    }

    /**
     * A locomotive renamed in its settings is renamed on its station's caption in the main window and in the page's
     * own window, and stays renamed through Sync of that locomotive, which reads the pages again.
     *
     * MUTATION: in `TrainControlUI.redrawRouteSquaresOrRebuild`, return at once instead of rebuilding where the pages
     * were read again, and this fails.
     */
    @Test(timeOut = 180000)
    public void testARenamedLocomotiveIsRenamedOnEveryCaptionThroughASync() throws Exception
    {
        final TileKey at = aTrainStandingAlone();
        final Locomotive train = ui.getAutonomyOccupantsForTile(at).get(0).getCurrentLocomotive();
        final String name = train.getName();
        final String renamed = "Zq" + name.substring(2);

        final LayoutPopupUI window = AskingWindows.openThePage(ui);

        try
        {
            assertTrue(captionIn(ui, at).contains(name.substring(0, 3)), "precondition: the caption at " + at
                + " says " + captionIn(ui, at));

            assertTrue(captionIn(window, at).contains(name.substring(0, 3)), "precondition: the page window's caption"
                + " at " + at + " says " + captionIn(window, at));

            AskingWindows.renameInSettings(ui, train, renamed);

            assertTrue(captionIn(ui, at).contains("Zq"), "the caption at " + at + " says " + captionIn(ui, at)
                + " after " + name + " was renamed " + renamed);

            assertTrue(captionIn(window, at).contains("Zq"), "the page window's caption at " + at + " says "
                + captionIn(window, at) + " after " + name + " was renamed " + renamed);

            final Component before = Components.onTheEventThread(() -> shownPage());

            syncTheLocomotive(train);

            assertNotSame(Components.onTheEventThread(() -> shownPage()), before,
                "Sync of " + renamed + " read the pages again"
                + " and did not draw the diagram afresh");

            assertTrue(captionIn(ui, at).contains("Zq"), "after Sync of " + renamed + " the caption at " + at
                + " says " + captionIn(ui, at));
        }
        finally
        {
            AskingWindows.renameInSettings(ui, train, name);

            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * A page edited elsewhere is drawn as edited after Sync of one locomotive, which reads the pages again from the
     * folder, and drawn as it was once the edit is undone and synced again.
     *
     * MUTATION: in `TrainControlUI.redrawRouteSquaresOrRebuild`, return at once instead of rebuilding where the pages
     * were read again, and this fails.
     */
    @Test(timeOut = 180000)
    public void testAPageEditedElsewhereIsRedrawnAfterASync() throws Exception
    {
        assertTrue(drawsTheSquare(4, 1), "precondition: the diagram does not draw the square at 4,1");

        outAndBackElsewhere(
            train -> assertFalse(drawsTheSquare(4, 1), "the square taken out of " + PAGE + " elsewhere is still drawn"
                + " after Sync of " + train.getName()),
            train -> assertTrue(drawsTheSquare(4, 1), "the square put back into " + PAGE + " is not drawn after"
                + " another Sync"));
    }

    /**
     * A page window is redrawn too when a sync reads the pages again (OB-462): the square taken out of its page
     * elsewhere is gone from the window after Sync of one locomotive, and back after the next.
     *
     * MUTATION: leave out `refreshPopupsWhosePagesWereReadAgain()` in `TrainControlUI.repaintLayout`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testAPageWindowIsRedrawnAfterASync() throws Exception
    {
        final LayoutPopupUI window = AskingWindows.openThePage(ui);

        try
        {
            assertTrue(drawsTheSquare(window, 4, 1), "precondition: the page window does not draw the square at 4,1");

            // The window's redraw is posted after the main window's
            outAndBackElsewhere(
                train -> assertTrue(Components.waitFor(() -> !drawsTheSquare(window, 4, 1), 20000), "the square taken"
                    + " out of " + PAGE + " elsewhere is still drawn in the page's window after Sync of "
                    + train.getName()),
                train -> assertTrue(Components.waitFor(() -> drawsTheSquare(window, 4, 1), 20000), "the square put"
                    + " back into " + PAGE + " is not drawn in the page's window after another Sync"));
        }
        finally
        {
            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * A page window closes once a sync finds its page gone from the layout (OB-466), rather than going on showing a
     * page that is no longer there.  The page taken out is one autonomy leaves out, so the setup is not at stake.
     *
     * MUTATION: leave out `closePopupsWhosePagesAreGone()` in `TrainControlUI.refreshPopupsWhosePagesWereReadAgain`,
     * and the window stays open.
     */
    @Test(timeOut = 180000)
    public void testAPageWindowClosesWhenASyncFindsItsPageGone() throws Exception
    {
        final OutsideEdit edit = new OutsideEdit();

        try
        {
            edit.write(edit.text.replace(edit.entry, ""), null);

            syncTheLocomotive(edit.train);

            assertFalse(model.getLayoutList().contains(RENAMED_FROM), "precondition: the sync did not read the pages"
                + " again");

            assertTrue(Components.waitFor(() -> !edit.window.isShowing(), 20000), "the page window of " + RENAMED_FROM
                + " is still open after a sync found the page gone");
        }
        finally
        {
            edit.putBack();
        }
    }

    /**
     * A page window follows its page when the page is renamed in the folder, outside TrainControl, and synced: it shows
     * the page under its new name, found by its page ID.
     *
     * MUTATION: in `LayoutPopupUI.showsAPageReadAgain`, look the page up by name alone, and the window keeps the old
     * page.
     */
    @Test(timeOut = 180000)
    public void testAPageWindowFollowsAPageRenamedOutside() throws Exception
    {
        final OutsideEdit edit = new OutsideEdit();

        try
        {
            edit.write(edit.text.replace(edit.entry, edit.entry.replace(RENAMED_FROM, RENAMED_TO)), RENAMED_TO);

            syncTheLocomotive(edit.train);

            assertTrue(model.getLayoutList().contains(RENAMED_TO), "precondition: the sync did not read the rename");

            final LayoutPopupUI window = edit.window;

            assertTrue(Components.waitFor(() -> window.isShowing() && RENAMED_TO.equals(window.getLayoutTitle()),
                20000), "the page window does not show " + RENAMED_TO + " after the page was renamed outside and"
                + " synced; showing: " + window.isShowing() + ", page: " + window.getLayoutTitle());
        }
        finally
        {
            edit.putBack();
        }
    }

    /**
     * A page window on `RENAMED_FROM` (page ID 4), opened from the main window, which then goes back to a page that
     * stays; and the folder's index and page file, kept to be put back.
     */
    private final class OutsideEdit
    {
        final Path config = sandbox.getFolder().toPath().resolve("config");
        final Path index = config.resolve("gleisbild.cs2");
        final Path page = config.resolve("gleisbilder/" + RENAMED_FROM + ".cs2");
        final Path moved = config.resolve("gleisbilder/" + RENAMED_TO + ".cs2");
        final byte[] indexWas;
        final byte[] pageWas;
        final String text;
        final String entry;
        final LayoutPopupUI window;
        final Locomotive train = model.getLocByName(model.getLocList().get(0));

        OutsideEdit() throws Exception
        {
            indexWas = Files.readAllBytes(index);
            pageWas = Files.readAllBytes(page);
            text = new String(indexWas, StandardCharsets.UTF_8);

            final String eol = text.contains("\r\n") ? "\r\n" : "\n";

            entry = ("seite\r\n .id=4\r\n .name=" + RENAMED_FROM + "\r\n").replace("\r\n", eol);

            assertTrue(text.contains(entry), "precondition: the index has no entry for " + RENAMED_FROM);

            Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), RENAMED_FROM);
            Settle.theDiagram(ui);

            window = AskingWindows.openThePage(ui);

            assertEquals(window.getLayoutTitle(), RENAMED_FROM, "precondition: the page window does not show "
                + RENAMED_FROM);

            showTheMainPage();
        }

        /** Writes the index, and moves the page's file to the name given, or deletes it for null. */
        void write(String newIndex, String pageNowNamed) throws Exception
        {
            Files.write(index, newIndex.getBytes(StandardCharsets.UTF_8));

            if (pageNowNamed == null) Files.delete(page);
            else Files.move(page, moved);
        }

        /** Puts the files back, syncs, and closes the window. */
        void putBack() throws Exception
        {
            Files.write(index, indexWas);
            Files.deleteIfExists(moved);
            Files.write(page, pageWas);

            syncTheLocomotive(train);

            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * A page window follows its page through a rename (OB-466): it shows the renamed page, not whichever page sits at
     * a place it once had.  The window is restored as a start restores a remembered one, with the main window on
     * another page.
     *
     * MUTATION: in `LayoutPopupUI.pageNow`, return the page found by name alone, and the window closes.
     */
    @Test(timeOut = 180000)
    public void testAPageWindowFollowsItsPageThroughARename() throws Exception
    {
        final java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();
        final boolean rememberWas = prefs.getBoolean(TrainControlUI.REMEMBER_WINDOW_LOCATION, false);
        final String key = TrainControlUI.LAYOUT_TITLES_PREF + "_" + aTileSize();

        prefs.putBoolean(TrainControlUI.REMEMBER_WINDOW_LOCATION, true);
        prefs.put(key, RENAMED_FROM);

        LayoutPopupUI window = null;

        try
        {
            final java.util.Set<java.awt.Window> before =
                new java.util.HashSet<>(Arrays.asList(java.awt.Window.getWindows()));

            SwingUtilities.invokeAndWait(ui::restoreLayoutTitles);

            window = AskingWindows.theNewPageWindow(before, "precondition: no page window was restored for "
                + RENAMED_FROM);

            assertEquals(window.getLayoutTitle(), RENAMED_FROM, "precondition: the restored window shows another page");

            renamePage(RENAMED_FROM, RENAMED_TO);

            final LayoutPopupUI renamed = window;

            assertTrue(Components.waitFor(() -> renamed.isShowing() && RENAMED_TO.equals(renamed.getLayoutTitle()),
                20000), "the page window does not show " + RENAMED_TO + " after its page was renamed; showing: "
                + renamed.isShowing() + ", page: " + renamed.getLayoutTitle());
        }
        finally
        {
            prefs.putBoolean(TrainControlUI.REMEMBER_WINDOW_LOCATION, rememberWas);
            prefs.remove(key);

            if (model.getLayoutList().contains(RENAMED_TO)) renamePage(RENAMED_TO, RENAMED_FROM);

            if (window != null) SwingUtilities.invokeAndWait(window::dispose);

            showTheMainPage();
        }
    }

    /**
     * A page edit that leaves a window's page the same size keeps the size the operator gave the window (OB-466): the
     * window is redrawn, not packed to its page again.
     *
     * MUTATION: in `LayoutPopupUI.refreshDiagram`, pass true to `goToLayoutPage` again, and the window is packed.
     */
    @Test(timeOut = 180000)
    public void testAPageEditKeepsTheSizeOfAPageWindow() throws Exception
    {
        Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), RENAMED_FROM);
        Settle.theDiagram(ui);

        final LayoutPopupUI window = AskingWindows.openThePage(ui);

        try
        {
            final java.awt.Dimension chosen = Components.onTheEventThread(() ->
                new java.awt.Dimension(window.getWidth() + 60, window.getHeight() + 40));

            SwingUtilities.invokeAndWait(() -> window.setSize(chosen));
            Components.settle();

            final Object gridBefore = Reflect.get(LayoutPopupUI.class, window, "grid");

            renamePage(RENAMED_FROM, RENAMED_TO);

            assertTrue(Components.waitFor(() -> gridOf(window) != gridBefore, 20000), "precondition: the page edit did"
                + " not redraw the page window");

            Components.settle();

            assertEquals(Components.onTheEventThread(window::getSize), chosen, "a page edit that left the page's size"
                + " alone packed the page window to its page again");
        }
        finally
        {
            if (model.getLayoutList().contains(RENAMED_TO)) renamePage(RENAMED_TO, RENAMED_FROM);

            SwingUtilities.invokeAndWait(window::dispose);

            showTheMainPage();
        }
    }

    /**
     * Pop Up All draws every page's window, then shows them in the list's order and does their shared work once
     * (FR-216): one pass over the captions, one update of the window list and one drawing of the locomotive, for all
     * five pages.
     *
     * MUTATION: in `TrainControlUI.showLayoutPopup`, leave out the `return` for a window already asked for, and each
     * window does the shared work again.
     */
    @Test(timeOut = 180000)
    public void testPopUpAllDoesTheSharedWorkOnce() throws Exception
    {
        final AtomicInteger asks = new AtomicInteger();
        final AtomicInteger renders = new AtomicInteger();
        final List<String> logged = java.util.Collections.synchronizedList(new ArrayList<>());
        final java.util.logging.Handler tap = support.LogTap.messagesInto(logged);
        final java.util.logging.Logger log = java.util.logging.Logger.getLogger(MarklinControlStation.class.getName());
        final DiagramMonitorDriver driver = ui.getDiagramMonitorDriver();
        List<LayoutPopupUI> windows = new ArrayList<>();

        Components.settle();

        // The captions' pass (`updateVisiblePoints`) tells the driver once each time it runs, with a setup loaded
        Reflect.set(TrainControlUI.class, ui, "diagramMonitorDriver", new DiagramMonitorDriver(ui,
            ui.getDiagramTileRegistry())
        {
            @Override
            public void recordedFacingsChanged()
            {
                asks.incrementAndGet();
                driver.recordedFacingsChanged();
            }
        });

        TrainControlUI.beforeARenderIsPosted = renders::incrementAndGet;
        log.addHandler(tap);

        try
        {
            windows = popUpAll();

            Components.settle();

            final String listed = I18n.f("layout.ui.infoCurrentlyActivePopups", "");
            final long updates;

            synchronized (logged)
            {
                updates = logged.stream().filter(line -> line.startsWith(listed)).count();
            }

            assertEquals(asks.get(), 1, "Pop Up All of " + windows.size() + " pages went over the captions "
                + asks.get() + " times");
            assertEquals(updates, 1L, "Pop Up All updated the list of page windows " + updates + " times: " + logged);
            assertEquals(renders.get(), 1, "Pop Up All drew the locomotive " + renders.get() + " times");
        }
        finally
        {
            log.removeHandler(tap);
            TrainControlUI.beforeARenderIsPosted = null;
            Reflect.set(TrainControlUI.class, ui, "diagramMonitorDriver", driver);

            closeAll(windows);
        }
    }

    /**
     * An edit to one page draws only that page's window again (FR-216): every other window keeps its grid and its
     * title, its squares registered again so the trains and marks still reach them.
     *
     * MUTATION: in `LayoutGrid.keep`, leave out the registry's `register`, and a kept window's squares are
     * registered nowhere.
     */
    @Test(timeOut = 180000)
    public void testAnEditDrawsOnlyThatPagesWindowAgain() throws Exception
    {
        final List<LayoutPopupUI> windows = popUpAll();

        try
        {
            final Map<LayoutPopupUI, Object> grids = gridsOf(windows);
            final Map<LayoutPopupUI, String> titles = new HashMap<>();

            for (LayoutPopupUI window : windows) titles.put(window, Components.onTheEventThread(window::getTitle));

            outAndBackElsewhere(train -> editingComplete(), train ->
            {
                for (LayoutPopupUI window : windows)
                {
                    final String page = window.getLayoutTitle();

                    if (PAGE.equals(page))
                    {
                        assertNotSame(gridOf(window), grids.get(window), "the window of the page edited was not drawn"
                            + " again");
                        assertFalse(drawsTheSquare(window, 4, 1), "the window of " + PAGE + " still draws the square"
                            + " taken out of it");

                        continue;
                    }

                    final org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) gridOf(window);

                    assertSame(grid, grids.get(window), "the edit to " + PAGE + " drew " + page + "'s window again");
                    assertTrue(Components.onTheEventThread(grid::tilesAreStillRegistered), page + "'s window kept"
                        + " its grid but its squares are registered nowhere, so no train or mark reaches them");
                    assertEquals(Components.onTheEventThread(window::getTitle), titles.get(window), page + "'s"
                        + " window changed its title");
                }
            }, train -> { });
        }
        finally
        {
            closeAll(windows);
        }
    }

    /**
     * Show Addresses draws every page window again, with the addresses and then without (FR-216): the tick changes
     * how every page looks.
     *
     * MUTATION: in `LayoutPopupUI.sameSquares`, compare the page's flag with that of the page the window holds rather
     * than the one it was drawn with, and the windows keep their old look.
     */
    @Test(timeOut = 180000)
    public void testShowAddressesDrawsEveryPageWindowAgain() throws Exception
    {
        final javax.swing.JCheckBoxMenuItem tick = WindowField.of(ui, "menuItemShowLayoutAddresses");
        final boolean was = tick.isSelected();
        final List<LayoutPopupUI> windows = popUpAll();

        try
        {
            for (boolean on : new boolean[] {!was, was})
            {
                SwingUtilities.invokeAndWait(tick::doClick);

                for (LayoutPopupUI window : windows)
                {
                    if (!hasAddresses(window.getLayoutTitle())) continue;

                    assertTrue(Components.waitFor(() -> showsAddresses(window) == on, 20000), window.getLayoutTitle()
                        + "'s window " + (on ? "shows no addresses after Show Addresses was ticked"
                        : "still shows addresses after Show Addresses was unticked"));
                }
            }
        }
        finally
        {
            if (tick.isSelected() != was) SwingUtilities.invokeAndWait(tick::doClick);

            closeAll(windows);
        }
    }

    /**
     * A page renamed draws again its own window, under the new name, and each window whose arrows lead to it, which
     * then name it (FR-216); a window whose page does not lead there keeps its grid.
     *
     * MUTATION: in `LayoutPopupUI.sameSquares`, leave out the linked page's name, and an arrow to the renamed page
     * still names the old one.
     */
    @Test(timeOut = 180000)
    public void testARenameDrawsTheWindowsThatLeadToThePageAgain() throws Exception
    {
        final String from = "2 - Bottom";
        final String to = "2 - Bottom renamed";
        final List<LayoutPopupUI> windows = popUpAll();

        try
        {
            final Map<LayoutPopupUI, Object> grids = gridsOf(windows);
            final Map<LayoutPopupUI, Boolean> leads = new HashMap<>();

            for (LayoutPopupUI window : windows) leads.put(window, !arrowsIn(window, from).isEmpty());

            assertTrue(leads.containsValue(true) && leads.containsValue(false), "precondition: no window, or every"
                + " window, has an arrow to " + from + ": " + leads);

            renamePage(from, to);

            for (LayoutPopupUI window : windows)
            {
                final String page = window.getLayoutTitle();

                if (to.equals(page)) continue;

                if (leads.get(window))
                {
                    assertTrue(Components.waitFor(() -> !arrowsIn(window, to).isEmpty() && arrowsIn(window, from)
                        .isEmpty(), 20000), page + "'s arrows still lead to " + from + " after it was renamed " + to);
                }
                else if (!from.equals(page))
                {
                    assertSame(gridOf(window), grids.get(window), "renaming " + from + " drew " + page + "'s window"
                        + " again, though nothing leads there from it");
                }
            }

            assertTrue(windows.stream().anyMatch(window -> to.equals(window.getLayoutTitle())), "no window shows "
                + to + " after its page was renamed");
        }
        finally
        {
            if (model.getLayoutList().contains(to)) renamePage(to, from);

            closeAll(windows);

            showTheMainPage();
        }
    }

    /**
     * Moving a page leaves every page window on its own page with its grid, as no page changed (FR-216).
     *
     * MUTATION: in `LayoutPopupUI.refreshDiagram`, pass false for keeping a page that draws the same, and every window
     * is drawn again.
     */
    @Test(timeOut = 180000)
    public void testMovingAPageKeepsEveryPageWindow() throws Exception
    {
        final List<String> order = new ArrayList<>(model.getLayoutList());
        final List<LayoutPopupUI> windows = popUpAll();

        try
        {
            final Map<LayoutPopupUI, Object> grids = gridsOf(windows);
            final Map<LayoutPopupUI, String> pages = new HashMap<>();

            for (LayoutPopupUI window : windows) pages.put(window, window.getLayoutTitle());

            movePage(PAGE, 1);

            assertNotEquals(model.getLayoutList(), order, "precondition: the page did not move");

            for (LayoutPopupUI window : windows)
            {
                assertEquals(window.getLayoutTitle(), pages.get(window), "a window left its page after a move");
                assertSame(gridOf(window), grids.get(window), "moving " + PAGE + " drew " + pages.get(window)
                    + "'s window again");
            }
        }
        finally
        {
            if (!model.getLayoutList().equals(order)) movePage(PAGE, -1);

            closeAll(windows);

            showTheMainPage();
        }
    }

    /**
     * A page window kept through an editor's Cancel runs a route as the route editor's Save left it (FR-216): a click
     * on its route square sends what a click on the main window's sends, not the commands the route had.
     *
     * MUTATION: in `LayoutGrid.keep`, leave every square on its old component (no `bindTo`, no counterpart check), and
     * the click sends the route's old commands.
     */
    @Test(timeOut = 180000)
    public void testAKeptWindowRunsARouteAsEdited() throws Exception
    {
        final LayoutDiagramComponent square = aRouteSquareWhere(route -> route.getRoute().size() > 1);
        final MarklinRoute route = (MarklinRoute) square.getRoute();
        final String name = route.getName();
        final List<org.traincontrol.base.RouteCommand> commands = new ArrayList<>(route.getRoute());
        final boolean wasLocked = route.isLocked();
        final LayoutPopupUI window = aKeptWindow();

        try
        {
            final int before = commandsOnAClick(labelOf(squareNow(square)));

            route.setLocked(false);

            // What the route editor's Save does
            assertTrue(model.editRoute(name, name, new ArrayList<>(commands.subList(0, commands.size() - 1)),
                route.getS88(), route.getTriggerType(), route.isEnabled(), route.getConditions()), "precondition: "
                + name + " was not edited");

            rebuildThePage();

            final int edited = commandsOnAClick(labelOf(squareNow(square)));

            assertNotEquals(edited, before, "precondition: the edit to " + name + " did not change what it sends");
            assertEquals(commandsOnAClick(squareIn(window, square)), edited, "a click on the kept page window's square"
                + " of " + name + " did not send the route as edited");
        }
        finally
        {
            final MarklinRoute now = (MarklinRoute) model.getRoute(name);

            now.setLocked(false);
            model.editRoute(name, name, commands, now.getS88(), now.getTriggerType(), now.isEnabled(),
                now.getConditions());
            ((MarklinRoute) model.getRoute(name)).setLocked(wasLocked);

            SwingUtilities.invokeAndWait(window::dispose);

            rebuildThePage();
        }
    }

    /**
     * A page window kept through an editor's Cancel lets go of a route renumbered, and then of one deleted (FR-216):
     * its square names no route and a click on it sends nothing.
     *
     * MUTATION: in `LayoutGrid.keep`, leave every square on its old component (no `bindTo`, no counterpart check), and
     * the square still runs the route.
     */
    @Test(timeOut = 180000)
    public void testAKeptWindowLetsGoOfARouteRenumberedOrDeleted() throws Exception
    {
        final LayoutDiagramComponent square = aBoundRouteSquare();
        final MarklinRoute route = (MarklinRoute) square.getRoute();
        final String name = route.getName();
        final int id = route.getId();
        final boolean wasLocked = route.isLocked();
        final LayoutPopupUI window = aKeptWindow();
        int highest = 999;

        for (String each : model.getRouteList()) highest = Math.max(highest, model.getRouteId(each));

        final String free = Integer.toString(highest + 1);

        route.setLocked(false);

        try
        {
            try (Answering answering = Answering.start(message -> Answering.type(free)))
            {
                SwingUtilities.invokeAndWait(() -> ui.changeRouteId(name));
            }

            Settle.theDiagram(ui);

            assertEquals(Integer.toString(route.getId()), free, "precondition: " + name + " was not renumbered");
            assertNull(squareIn(window, square).getComponent().getRoute(), "the kept page window's square still holds"
                + " " + name + " after it was renumbered");
            assertEquals(commandsOnAClick(squareIn(window, square)), 0, "a click on the kept page window's square ran "
                + name + " after it was renumbered");

            model.changeRouteId(name, id);

            try (Answering answering = Answering.start(message -> Answering.press(AskingWindows.YES)))
            {
                SwingUtilities.invokeAndWait(() -> ui.deleteRoute(name));
            }

            Settle.theDiagram(ui);

            assertNull(model.getRoute(name), "precondition: " + name + " was not deleted");
            assertNull(squareIn(window, square).getComponent().getRoute(), "the kept page window's square still holds"
                + " " + name + " after it was deleted");
            assertEquals(commandsOnAClick(squareIn(window, square)), 0, "a click on the kept page window's square ran "
                + name + " after it was deleted");
        }
        finally
        {
            if (model.getRoute(name) == null)
            {
                model.newRoute(name, id, route.getRoute(), route.getS88(), route.getTriggerType(), route.isEnabled(),
                    route.getConditions());
            }
            else if (model.getRouteId(name) != id)
            {
                model.changeRouteId(name, id);
            }

            ((MarklinRoute) model.getRoute(name)).setLocked(wasLocked);

            SwingUtilities.invokeAndWait(window::dispose);

            rebuildThePage();
        }
    }

    /**
     * A page window that cannot be drawn costs only itself (FR-216): asked for together with two others, as Pop Up All
     * asks, with its page gone by the time they are drawn, the other two still open, and a window asked for afterwards
     * opens too.
     *
     * MUTATION: in `TrainControlUI.showLayoutPopup`, catch only an IllegalArgumentException from a window's drawing,
     * and the batch stops at the one that fails.
     */
    @Test(timeOut = 180000)
    public void testAWindowThatCannotBeDrawnCostsOnlyItself() throws Exception
    {
        final java.lang.reflect.Method show = Reflect.method(TrainControlUI.class, "showLayoutPopup", String.class,
            int.class);
        final int size = aTileSize();
        final java.util.Set<java.awt.Window> before =
            new java.util.HashSet<>(Arrays.asList(java.awt.Window.getWindows()));

        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    for (String page : new String[] {PAGE, "a page gone", RENAMED_FROM}) show.invoke(ui, page, size);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertTrue(Components.waitFor(() -> openedSince(before).size() == 2, 20000), "a window whose page was gone"
                + " stopped the others asked for with it; open: " + openedSince(before));

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    show.invoke(ui, PAGE, size);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertTrue(Components.waitFor(() -> openedSince(before).size() == 3, 20000), "a window asked for after one"
                + " that could not be drawn did not open");
        }
        finally
        {
            closeAll(openedSince(before));
        }
    }

    /**
     * A square turned on a page draws that page's window again (FR-216): the same squares stand in the same places, but
     * the page reads differently.
     *
     * MUTATION: in `LayoutPopupUI.sameSquares`, leave out `sameText`, and the window keeps its old drawing.
     */
    @Test(timeOut = 180000)
    public void testATurnedSquareDrawsItsWindowAgain() throws Exception
    {
        final LayoutPopupUI window = AskingWindows.openThePage(ui);

        try
        {
            final Object grid = gridOf(window);

            editedElsewhere(square -> square.replaceFirst("( \\.typ=gerade)(\\r?\\n)", "$1$2 .drehung=1$2"),
                train -> editingComplete(), train ->
                {
                    assertNotSame(gridOf(window), grid, "the page window kept its drawing of " + PAGE + " after a"
                        + " square on it was turned");

                    final LayoutLabel turned = Components.onTheEventThread(() -> Components.find(window,
                        LayoutLabel.class, each -> each.getComponent() != null && each.getComponent().getX() == 4
                        && each.getComponent().getY() == 1));

                    assertEquals(turned.getComponent().getOrientation(), 1, "the page window does not draw the square"
                        + " turned");
                }, train -> { });
        }
        finally
        {
            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * A station renamed on a page draws that page's window again (FR-216): the page reads the same, but its captions do
     * not.
     *
     * MUTATION: in `LayoutPopupUI.sameSquares`, leave out `captionsAreAsBuilt`, and the window keeps its old captions.
     */
    @Test(timeOut = 180000)
    public void testARenamedStationDrawsItsWindowAgain() throws Exception
    {
        // The setup now: a refresh earlier in the class replaces the one the class opened on
        final AutonomySession now = ui.getAutonomySession();
        TileKey station = null;

        for (TileKey each : now.getLabelledStationTiles())
        {
            if (station == null && PAGE.equals(each.getPage()) && now.getLocomotiveNameAt(each) == null
                && now.getStore().getPointName(each) != null) station = each;
        }

        assertNotNull(station, "precondition: " + PAGE + " has no named station with no train at it");

        final TileKey renamed = station;
        final String name = now.getStore().getPointName(renamed);
        final LayoutPopupUI window = AskingWindows.openThePage(ui);

        try
        {
            final Object grid = gridOf(window);

            SwingUtilities.invokeAndWait(() ->
            {
                now.setPointName(renamed, name + " renamed");
                ui.autonomySetupChanged();
            });

            assertTrue(Components.waitFor(() -> gridOf(window) != grid, 20000), "the page window kept its captions"
                + " after the station " + name + " on " + PAGE + " was renamed");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                now.setPointName(renamed, name);
                ui.autonomySetupChanged();
            });

            Settle.theDiagram(ui);

            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * A page window keeps its addresses through the doors that read the pages again, an editor's Cancel and a sync, on
     * a page the main window does not show; with Show Addresses unticked it shows none through them either.
     *
     * MUTATION: leave out `ensureShowLayoutPreference()` in `TrainControlUI.repaintLayout`, and the window loses its
     * addresses at the editor's Cancel.
     */
    @Test(timeOut = 180000)
    public void testAPageWindowKeepsItsAddressesThroughAReadAgain() throws Exception
    {
        final javax.swing.JCheckBoxMenuItem tick = WindowField.of(ui, "menuItemShowLayoutAddresses");
        final boolean was = tick.isSelected();
        final Locomotive train = model.getLocByName(model.getLocList().get(0));

        Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), "2 - Bottom");
        Settle.theDiagram(ui);

        final LayoutPopupUI window = AskingWindows.openThePage(ui);

        showTheMainPage();

        try
        {
            for (boolean on : new boolean[] {true, false})
            {
                if (tick.isSelected() != on) SwingUtilities.invokeAndWait(tick::doClick);

                assertTrue(Components.waitFor(() -> showsAddresses(window) == on, 20000), "precondition: the window"
                    + " of 2 - Bottom does not follow Show Addresses " + (on ? "ticked" : "unticked"));

                editingComplete();

                assertEquals(showsAddresses(window), on, "the window of 2 - Bottom " + (on ? "lost" : "took")
                    + " its addresses at an editor's Cancel, with Show Addresses " + (on ? "ticked" : "unticked"));

                syncTheLocomotive(train);

                assertEquals(showsAddresses(window), on, "the window of 2 - Bottom " + (on ? "lost" : "took")
                    + " its addresses at a sync, with Show Addresses " + (on ? "ticked" : "unticked"));
            }
        }
        finally
        {
            if (tick.isSelected() != was) SwingUtilities.invokeAndWait(tick::doClick);

            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    // ---------------------------------------------------------------- the fixture

    // A page autonomy leaves out, so a rename does not put the setup at stake
    private static final String RENAMED_FROM = "5 - Test";
    private static final String RENAMED_TO = "5 - Test renamed";

    /** Renames a page through Layouts &gt; Rename's own door, past its prompt, and waits for the pages read again. */
    private static void renamePage(String from, String to) throws Exception
    {
        Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), from);
        Settle.theDiagram(ui);

        final Object actions = Reflect.get(TrainControlUI.class, ui, "layoutFileActions");
        final java.lang.reflect.Method rename = Reflect.method(actions.getClass(), "duplicateOrRenameCurrentLayout",
            String.class, boolean.class, boolean.class, boolean.class,
            org.traincontrol.gui.layouteditor.LayoutEditor.class);
        final java.util.concurrent.atomic.AtomicInteger underWay = (java.util.concurrent.atomic.AtomicInteger)
            Reflect.get(TrainControlUI.class, ui, "refreshesUnderWay");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    rename.invoke(actions, to, true, false, false, null);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertTrue(Components.waitFor(() -> model.getLayoutList().contains(to) && underWay.get() == 0, 30000),
                "precondition: " + from + " was not renamed " + to + "; asked: " + answering.asked());
        }

        Settle.theDiagram(ui);
        Components.settle();
    }

    /** Presses the Layout tab's All button and waits for every page's window, which must open in the list's order. */
    private static List<LayoutPopupUI> popUpAll() throws Exception
    {
        final List<LayoutPopupUI> opened = java.util.Collections.synchronizedList(new ArrayList<>());
        final java.awt.event.AWTEventListener listener = event ->
        {
            if (event.getID() == java.awt.event.WindowEvent.WINDOW_OPENED && event.getSource() instanceof LayoutPopupUI)
            {
                opened.add((LayoutPopupUI) event.getSource());
            }
        };

        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener, java.awt.AWTEvent.WINDOW_EVENT_MASK);

        try
        {
            Components.press(Components.named(ui, "popUpAllButton", javax.swing.JButton.class));

            assertTrue(Components.waitFor(() -> opened.size() == model.getLayoutList().size(), 30000), "Pop Up All"
                + " opened " + opened.size() + " windows for " + model.getLayoutList());

            Components.settle();
        }
        finally
        {
            java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        }

        final List<String> titles = new ArrayList<>();

        for (LayoutPopupUI window : opened) titles.add(window.getLayoutTitle());

        assertEquals(titles, model.getLayoutList(), "Pop Up All opened the windows in another order");

        return new ArrayList<>(opened);
    }

    private static void closeAll(List<LayoutPopupUI> windows) throws Exception
    {
        for (LayoutPopupUI window : windows) SwingUtilities.invokeAndWait(window::dispose);
    }

    private static Map<LayoutPopupUI, Object> gridsOf(List<LayoutPopupUI> windows)
    {
        final Map<LayoutPopupUI, Object> grids = new HashMap<>();

        for (LayoutPopupUI window : windows) grids.put(window, gridOf(window));

        return grids;
    }

    /** The track editor's Save or Cancel: its hand-over reads every page again, waited out with the diagram. */
    private static void editingComplete() throws Exception
    {
        final AtomicInteger underWay = (AtomicInteger) Reflect.get(TrainControlUI.class, ui, "refreshesUnderWay");

        SwingUtilities.invokeAndWait(ui::layoutEditingCompleteFromTheEditor);

        assertTrue(Components.waitFor(() -> underWay.get() == 0, 30000), "the pages were not read again");

        Settle.theDiagram(ui);
        Components.settle();
    }

    /** Moves a page through Move Page's own door and waits for the pages read again. */
    private static void movePage(String page, int by) throws Exception
    {
        Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), page);
        Settle.theDiagram(ui);

        final Object actions = Reflect.get(TrainControlUI.class, ui, "layoutFileActions");
        final java.lang.reflect.Method move = Reflect.method(actions.getClass(), "movePage", int.class,
            org.traincontrol.gui.layouteditor.LayoutEditor.class);
        final AtomicInteger underWay = (AtomicInteger) Reflect.get(TrainControlUI.class, ui, "refreshesUnderWay");
        final List<String> before = new ArrayList<>(model.getLayoutList());

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    move.invoke(actions, by, null);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertTrue(Components.waitFor(() -> !model.getLayoutList().equals(before) && underWay.get() == 0, 30000),
                "precondition: " + page + " did not move; asked: " + answering.asked());
        }

        Settle.theDiagram(ui);
        Components.settle();
    }

    /** Whether the model's page has a square that shows its address when Show Addresses is ticked. */
    private static boolean hasAddresses(String page)
    {
        return model.getLayout(page).getAll().stream().anyMatch(c -> !c.isText() && c.isClickable());
    }

    private static boolean showsAddresses(LayoutPopupUI window)
    {
        return !Components.all(window, org.traincontrol.gui.widgets.AddressLabel.class, null).isEmpty();
    }

    /** The arrows in a window that lead to this page. */
    private static List<LayoutLabel> arrowsIn(LayoutPopupUI window, String page)
    {
        return Components.all(window, LayoutLabel.class, each -> each.getComponent() != null
            && page.equals(each.getComponent().getLinkPageName()));
    }

    /** The page shown in its own window, kept through the track editor's hand-over (its Save or Cancel). */
    private static LayoutPopupUI aKeptWindow() throws Exception
    {
        final LayoutPopupUI window = AskingWindows.openThePage(ui);
        final Object grid = gridOf(window);

        editingComplete();

        assertSame(gridOf(window), grid, "precondition: the hand-over drew the page window again");

        return window;
    }

    /** The label a page window draws where a square of its page is. */
    private static LayoutLabel squareIn(LayoutPopupUI window, LayoutDiagramComponent square) throws Exception
    {
        final LayoutLabel found = Components.onTheEventThread(() -> Components.find(window, LayoutLabel.class, each ->
            each.getComponent() != null && each.getComponent().getX() == square.getX()
            && each.getComponent().getY() == square.getY()));

        assertNotNull(found, "precondition: the page window draws nothing where " + square + " is");

        return found;
    }

    /** How many accessory commands a click on a square sends, the power question answered Proceed. */
    private static int commandsOnAClick(LayoutLabel square) throws Exception
    {
        final support.WireRecorder wire = support.WireRecorder.open();

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("layout.ui.optionProceed"))))
        {
            final int mark = wire.mark();
            final long[] heard = {System.currentTimeMillis(), -1};

            support.Mouse.click(square);

            // Done once no accessory command has come for two seconds: a route sends its commands a beat apart
            Components.waitFor(() ->
            {
                long now = wire.since(mark).stream().filter(line -> line.contains("Type: Accessory")).count();

                if (now != heard[1])
                {
                    heard[1] = now;
                    heard[0] = System.currentTimeMillis();
                }

                return System.currentTimeMillis() - heard[0] > 2000;
            }, 20000);

            return (int) heard[1];
        }
        finally
        {
            wire.close();
        }
    }

    /** The page windows showing that were not open when `before` was taken. */
    private static List<LayoutPopupUI> openedSince(java.util.Set<java.awt.Window> before)
    {
        final List<LayoutPopupUI> opened = new ArrayList<>();

        for (java.awt.Window window : java.awt.Window.getWindows())
        {
            if (window instanceof LayoutPopupUI && window.isShowing() && !before.contains(window))
            {
                opened.add((LayoutPopupUI) window);
            }
        }

        return opened;
    }

    private static Object gridOf(LayoutPopupUI window)
    {
        try
        {
            return Reflect.get(LayoutPopupUI.class, window, "grid");
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** One of the window's tile sizes, under which a page window is remembered. */
    @SuppressWarnings("unchecked")
    private static int aTileSize() throws Exception
    {
        return ((Map<String, Integer>) Reflect.get(TrainControlUI.class, ui, "layoutSizes")).values().iterator().next();
    }

    /** The diagram on screen and the cached pages, to say afterwards that a door kept them. */
    private static final class Kept
    {
        final Component shown;
        final Map<String, JPanel> cached;

        Kept(Component shown, Map<String, JPanel> cached)
        {
            this.shown = shown;
            this.cached = cached;
        }

        void assertKept(String door) throws Exception
        {
            assertSame(Components.onTheEventThread(() -> shownPage()), shown, door + " drew the diagram afresh");

            Map<String, JPanel> now = Components.onTheEventThread(() -> new HashMap<>(ui.layoutCache));

            for (Map.Entry<String, JPanel> page : cached.entrySet())
            {
                assertSame(now.get(page.getKey()), page.getValue(), door + " threw away the cached " + page.getKey());
            }
        }
    }

    /** The diagram shown and every page cached, after another page has been drawn so there is one to keep. */
    private static Kept keptNow() throws Exception
    {
        Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), "2 - Bottom");
        Settle.theDiagram(ui);
        Components.select((JComboBox<?>) WindowField.of(ui, "LayoutList"), PAGE);
        Settle.theDiagram(ui);

        Map<String, JPanel> cached = Components.onTheEventThread(() -> new HashMap<>(ui.layoutCache));

        assertTrue(cached.size() > 1, "precondition: no other page is cached: " + cached.keySet());

        return new Kept(Components.onTheEventThread(() -> shownPage()), cached);
    }

    /** A route square on the page shown whose route the database has. */
    private static LayoutDiagramComponent aBoundRouteSquare() throws Exception
    {
        return aRouteSquareWhere(route -> true);
    }

    /** A route square on the page shown whose route the database has and passes `which`. */
    private static LayoutDiagramComponent aRouteSquareWhere(java.util.function.Predicate<MarklinRoute> which)
        throws Exception
    {
        LayoutDiagramComponent found = Components.onTheEventThread(() ->
        {
            for (LayoutLabel square : Components.all(shownPage(), LayoutLabel.class, each -> each.getComponent() != null
                && each.getComponent().isRoute() && each.getComponent().getRoute() instanceof MarklinRoute
                && which.test((MarklinRoute) each.getComponent().getRoute())))
            {
                return square.getComponent();
            }

            return null;
        });

        assertNotNull(found, "precondition: no route square on " + PAGE + " has its route, one that will do");

        return found;
    }

    /** The label drawing this component on the page shown, or null. */
    private static LayoutLabel labelOf(LayoutDiagramComponent c) throws Exception
    {
        return Components.onTheEventThread(() ->
            Components.find(shownPage(), LayoutLabel.class, each -> each.getComponent() == c));
    }

    /** The component drawn now where this one was. */
    private static LayoutDiagramComponent squareNow(LayoutDiagramComponent was) throws Exception
    {
        return Components.onTheEventThread(() ->
        {
            LayoutLabel now = Components.find(shownPage(), LayoutLabel.class, each -> each.getComponent() != null
                && each.getComponent().getX() == was.getX() && each.getComponent().getY() == was.getY());

            return now == null ? null : now.getComponent();
        });
    }

    private static String tipOf(LayoutDiagramComponent c) throws Exception
    {
        LayoutLabel label = labelOf(c);

        assertNotNull(label, "precondition: the square is not on the diagram shown");

        return Components.onTheEventThread(() -> label.getToolTipText());
    }

    /** What a claim asks of the diagram, given the locomotive synced. */
    private interface Check
    {
        void check(Locomotive train) throws Exception;
    }

    /**
     * Takes the plain track square at 4,1 (id 0x104) out of the page's file, as an edit elsewhere, and syncs a
     * locomotive, which reads the pages again; asks `out`, then puts the square back, syncs again and asks `back`.
     */
    private static void outAndBackElsewhere(Check out, Check back) throws Exception
    {
        outAndBackElsewhere(testTheDiagramIsKeptForWhatItDoesNotShow::syncTheLocomotive, out, back);
    }

    /** As {@link #outAndBackElsewhere(Check, Check)}, with `door` reading the pages again after the edit. */
    private static void outAndBackElsewhere(Check door, Check out, Check back) throws Exception
    {
        editedElsewhere(square -> "", door, out, back);
    }

    /** As {@link #outAndBackElsewhere(Check, Check, Check)}, the plain track at 0x104 made what `edit` makes of it. */
    private static void editedElsewhere(java.util.function.UnaryOperator<String> edit, Check door, Check out,
        Check back) throws Exception
    {
        final Path file = sandbox.getFolder().toPath().resolve("config/gleisbilder/" + PAGE + ".cs2");
        final byte[] asItWas = Files.readAllBytes(file);
        final String text = new String(asItWas, StandardCharsets.UTF_8);
        final String eol = text.contains("\r\n") ? "\r\n" : "\n";
        final String square = "element\r\n .id=0x104\r\n .typ=gerade\r\n .artikel=-1\r\n".replace("\r\n", eol);

        assertTrue(text.contains(square), "precondition: " + PAGE + " has no plain track at 0x104");

        final Locomotive train = model.getLocByName(model.getLocList().get(0));

        try
        {
            Files.write(file, text.replace(square, edit.apply(square)).getBytes(StandardCharsets.UTF_8));

            door.check(train);

            out.check(train);
        }
        finally
        {
            Files.write(file, asItWas);

            syncTheLocomotive(train);
        }

        back.check(train);
    }

    /** The page shown, for a wait that cannot throw. */
    private static Container shownPageNow()
    {
        try
        {
            return Components.onTheEventThread(() -> shownPage());
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** Whether the page shown draws something on this square. */
    private static boolean drawsTheSquare(int x, int y) throws Exception
    {
        return drawsTheSquare(shownPage(), x, y);
    }

    /** Whether a panel or window draws something on this square of its page. */
    private static boolean drawsTheSquare(Container in, int x, int y)
    {
        try
        {
            return Components.onTheEventThread(() ->
                Components.find(in, LayoutLabel.class, each -> each.getComponent() != null
                && each.getComponent().getX() == x && each.getComponent().getY() == y) != null);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** Disarms a route made here, which writes it anew, and waits for the diagram it draws. */
    private static void disarm(MarklinRoute route) throws Exception
    {
        ui.enableOrDisableRoute(route.getName(), false);

        assertTrue(Components.waitFor(() -> model.getRoute(route.getName()) != route, 20000),
            "precondition: disarming did not write the route anew");

        Settle.theDiagram(ui);
    }

    /** Puts a route back as it was, locked or not and armed or not, and draws the page afresh. */
    private static void putBack(String name, boolean wasLocked, boolean wasEnabled) throws Exception
    {
        MarklinRoute now = (MarklinRoute) model.getRoute(name);

        if (now != null)
        {
            now.setLocked(wasLocked);

            if (now.isEnabled() != wasEnabled) model.editRoute(now.getName(), now.getName(), now.getRoute(),
                now.getS88(), now.getTriggerType(), wasEnabled, now.getConditions());
        }

        rebuildThePage();
    }

    /** The labels a route redraws when it runs. */
    @SuppressWarnings("unchecked")
    private static java.util.Set<LayoutLabel> tilesOf(MarklinRoute route) throws Exception
    {
        return (java.util.Set<LayoutLabel>) Reflect.get(MarklinRoute.class, route, "tiles");
    }

    /**
     * Sync from the main window reads a page changed elsewhere, and the autonomy setup then describes the pages read,
     * not the ones it was opened on: the edit would otherwise reach the diagram and not the graph trains are sent over.
     *
     * MUTATION: leave out `followIfReadAgain(before)` in `TrainControlUI.doSync`, and this fails.
     */
    @Test(timeOut = 240000)
    public void testTheSetupFollowsThePagesSyncReadsAgain() throws Exception
    {
        final Check follows = train -> assertTrue(theSetupHoldsThePagesNow(), "after Sync the autonomy setup still"
            + " describes the pages it was opened on");

        outAndBackElsewhere(train -> pressSync(), follows, follows);
    }

    /**
     * Sync of one locomotive, which reads the pages again from a local folder too, leaves the setup describing a page
     * changed elsewhere.
     *
     * MUTATION: leave out `followIfReadAgain(before)` in `TrainControlUI.redrawRouteSquaresOrRebuild`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheSetupFollowsThePagesALocomotiveSyncReadsAgain() throws Exception
    {
        final Check follows = train -> assertTrue(theSetupHoldsThePagesNow(), "after Sync of a locomotive the autonomy"
            + " setup still describes the pages it was opened on");

        outAndBackElsewhere(follows, follows);
    }

    /**
     * Pages read again unchanged leave the setup and its configuration alone: following them resets the session and
     * reloads the configuration, which a route toggle or a locomotive's Sync does not ask for.
     *
     * MUTATION: in `TrainControlUI.followThePagesIfChanged`, follow without asking `thePagesChanged()`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testPagesReadAgainUnchangedKeepTheSetup() throws Exception
    {
        final Object before = Components.onTheEventThread(() -> ui.getAutonomySession());
        final String active = Components.onTheEventThread(() -> ui.getActiveDiagramConfiguration());

        syncTheLocomotive(model.getLocByName(model.getLocList().get(0)));

        Components.settle();

        assertSame(Components.onTheEventThread(() -> ui.getAutonomySession()), before, "a Sync that read the pages"
            + " again unchanged replaced the autonomy setup");

        assertEquals(Components.onTheEventThread(() -> ui.getActiveDiagramConfiguration()), active,
            "a Sync that read the pages again unchanged changed the configuration loaded");
    }

    /**
     * Sync of one locomotive during a run, with a page changed elsewhere, asks nothing and leaves the running
     * configuration and its setup alone; the follow is owed, and made once the run is over.
     *
     * MUTATION: in `TrainControlUI.followThePagesIfChanged`, follow without asking `aRunHoldsTheSetup()`, and this
     * fails.
     */
    @Test(timeOut = 240000)
    public void testALocomotiveSyncDuringARunLeavesTheRunAlone() throws Exception
    {
        duringARun(train -> syncTheLocomotive(train), false);
    }

    /**
     * The same, with an editor on its way as the run ends: the follow stays owed while the editor is coming, so the
     * setup is not reset under it, and is made once the editor has gone.
     *
     * MUTATION: in `TrainControlUI.followThePagesIfChanged`, leave out `|| anEditorIsOpenOrOnItsWay()`, and this fails.
     */
    @Test(timeOut = 240000)
    public void testAFollowARunHeldBackWaitsForAnEditor() throws Exception
    {
        duringARun(train -> syncTheLocomotive(train), true);
    }

    /**
     * Disarming a route the station carries during a run, with a page changed elsewhere, asks nothing and leaves the
     * running configuration and its setup alone; the follow is owed, and made once the run is over.
     *
     * MUTATION: in `TrainControlUI.followThePagesIfChanged`, follow without asking `aRunHoldsTheSetup()`, and this
     * fails.
     */
    @Test(timeOut = 240000)
    public void testAStationRouteDisarmedDuringARunLeavesTheRunAlone() throws Exception
    {
        final MarklinRoute route = (MarklinRoute) model.getRoute(model.getRouteList().get(0));
        final boolean wasLocked = route.isLocked();
        final boolean wasEnabled = route.isEnabled();

        try
        {
            // As a route the station carries, whose write syncs
            route.setLocked(true);

            duringARun(train ->
            {
                ui.enableOrDisableRoute(route.getName(), false);

                assertTrue(Components.waitFor(() -> model.getRoute(route.getName()) != route, 30000),
                    "precondition: disarming did not write the route anew");

                // The write comes before the sync it owes; the edit drawn says that sync and its redraw have run
                assertTrue(Components.waitFor(() -> shownPageNow() != null && !drawsTheSquare(shownPageNow(), 4, 1),
                    60000),
                    "precondition: the sync after disarming did not read the page edited elsewhere");

                Settle.theDiagram(ui);
            }, false);
        }
        finally
        {
            putBack(route.getName(), wasLocked, wasEnabled);
        }
    }

    /**
     * A page changed elsewhere, then `door` while the railway reads as running: nothing is asked, the configuration
     * and the setup stay; once the run is over the setup follows the pages, with the configuration still loaded.
     * `anEditor`: an editor is on its way as the run ends, and the follow waits for it to go.
     */
    private static void duringARun(Check door, boolean anEditor) throws Exception
    {
        final String active = Components.onTheEventThread(() -> ui.getActiveDiagramConfiguration());
        final Object before = Components.onTheEventThread(() -> ui.getAutonomySession());
        final org.traincontrol.automation.Layout railway = model.getAutoLayout();
        final java.lang.reflect.Field running = Reflect.field(org.traincontrol.automation.Layout.class, "running");
        final String stops = I18n.t("autolayout.ui.confirmReloadJsonStopsRunningLocomotives");

        assertNotNull(active, "precondition: no configuration is loaded");

        try (Answering answering = Answering.start(message -> Answering.pressAt(1)))
        {
            outAndBackElsewhere(train ->
            {
                running.setBoolean(railway, true);

                door.check(train);
            }, train ->
            {
                Components.settle();

                assertFalse(answering.asked().contains(stops), "a door during a run asked to stop the trains: "
                    + answering.asked());

                assertEquals(Components.onTheEventThread(() -> ui.getActiveDiagramConfiguration()), active,
                    "a door during a run unloaded the configuration running");

                assertSame(Components.onTheEventThread(() -> ui.getAutonomySession()), before,
                    "a door during a run replaced the autonomy setup under the running railway");

                running.setBoolean(railway, false);

                if (anEditor) Reflect.set(TrainControlUI.class, ui, "editorOnItsWay", true);

                // What the end of a run calls
                ui.updateVisiblePoints();

                if (anEditor)
                {
                    Components.settle();

                    assertSame(Components.onTheEventThread(() -> ui.getAutonomySession()), before,
                        "the follow the run held back reset the autonomy setup under an editor on its way");

                    // The editor does not come after all
                    Reflect.set(TrainControlUI.class, ui, "editorOnItsWay", false);
                    Reflect.method(TrainControlUI.class, "editorGone").invoke(ui);
                }

                assertTrue(Components.waitFor(testTheDiagramIsKeptForWhatItDoesNotShow::theSetupHoldsThePagesNow,
                    20000), "once the run was over the"
                    + " autonomy setup still described the pages from before the edit");

                assertEquals(Components.onTheEventThread(() -> ui.getActiveDiagramConfiguration()), active,
                    "following the pages once the run was over unloaded the configuration");
            }, train -> { });
        }
        finally
        {
            running.setBoolean(railway, false);
            Reflect.set(TrainControlUI.class, ui, "editorOnItsWay", false);
        }
    }

    /** Sync from the main window, whose failure after the pages (the stand-in has no routes) is closed. */
    private static void pressSync() throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.pressAt(1)))
        {
            SwingUtilities.invokeLater(() -> ui.doSync(ui));

            assertTrue(answering.awaitAsked(1, 60000), "Sync said nothing");

            Settle.theDiagram(ui);
        }
    }

    /** Whether the autonomy setup's pages are the objects the model holds now. */
    private static boolean theSetupHoldsThePagesNow()
    {
        try
        {
            return Components.onTheEventThread(() ->
            {
                AutonomySession now = ui.getAutonomySession();

                if (now == null || now.getPages().isEmpty()) return false;

                for (org.traincontrol.base.LayoutDiagram page : now.getPages())
                {
                    if (page != model.getLayout(page.getName())) return false;
                }

                return true;
            });
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** Sync in Locomotive Settings, waited out with the diagram it draws. */
    private static void syncTheLocomotive(Locomotive train) throws Exception
    {
        final CountDownLatch done = new CountDownLatch(1);

        ui.syncLocomotiveFrom(train, null, done::countDown);

        assertTrue(done.await(60, TimeUnit.SECONDS), "Sync of " + train.getName() + " did not finish");

        Settle.theDiagram(ui);
    }

    /** A named, captioned station of the page shown with one train standing at it. */
    private static TileKey aTrainStandingAlone() throws Exception
    {
        final Component page = Components.onTheEventThread(() -> shownPage());

        for (TileKey square : session.getGraph().getTiles().keySet())
        {
            if (ui.getAutonomyOccupantsForTile(square).size() != 1 || session.getStore().getPointName(square) == null)
            {
                continue;
            }

            if (!captionIn((Container) page, square).trim().isEmpty()) return square;
        }

        throw new AssertionError("precondition: no train stands alone at a named, captioned station on " + PAGE);
    }

    /** What the station's captions inside this window or panel say, together. */
    private static String captionIn(Container inside, TileKey square) throws Exception
    {
        return Components.onTheEventThread(() ->
        {
            StringBuilder said = new StringBuilder();

            for (JLabel label : ui.getLayoutStations(square))
            {
                if (SwingUtilities.isDescendingFrom(label, inside)) said.append(label.getText()).append(' ');
            }

            return said.toString();
        });
    }

    /** Draws the page shown afresh and waits for it. */
    private static void rebuildThePage() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.repaintLayout());

        Settle.theDiagram(ui);
    }

    private static String cacheKey(String page) throws Exception
    {
        return page + " " + ((JComboBox<?>) WindowField.of(ui, "SizeList")).getSelectedItem();
    }

    private static Container shownPage() throws Exception
    {
        JPanel inner = (JPanel) WindowField.of(ui, "InnerLayoutPanel");

        return inner.getComponentCount() == 0 ? null : (Container) inner.getComponent(0);
    }
}
