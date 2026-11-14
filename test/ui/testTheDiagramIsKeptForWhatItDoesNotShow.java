package ui;

import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
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
 * update).  On a copy of the frozen railway, syncing with a stand-in station.
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
     * A page window's route square follows a route written anew as the main window's does: disarming a route made here,
     * the window's square shows the new route running, without a redraw of the window.
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
        final LayoutPopupUI window = AskingWindows.openThePage(ui);

        route.setLocked(false);

        try
        {
            final LayoutLabel inWindow = Components.onTheEventThread(() ->
                Components.find(window, LayoutLabel.class, each -> each.getComponent() == square));

            assertNotNull(inWindow, "precondition: the page window does not draw the route square of " + PAGE);

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

    // ---------------------------------------------------------------- the fixture

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
        LayoutDiagramComponent found = Components.onTheEventThread(() ->
        {
            for (LayoutLabel square : Components.all(shownPage(), LayoutLabel.class, each -> each.getComponent() != null
                && each.getComponent().isRoute() && each.getComponent().getRoute() instanceof MarklinRoute))
            {
                return square.getComponent();
            }

            return null;
        });

        assertNotNull(found, "precondition: no route square on " + PAGE + " has its route");

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
        final Path file = sandbox.getFolder().toPath().resolve("config/gleisbilder/" + PAGE + ".cs2");
        final byte[] asItWas = Files.readAllBytes(file);
        final String text = new String(asItWas, StandardCharsets.UTF_8);
        final String eol = text.contains("\r\n") ? "\r\n" : "\n";
        final String square = "element\r\n .id=0x104\r\n .typ=gerade\r\n .artikel=-1\r\n".replace("\r\n", eol);

        assertTrue(text.contains(square), "precondition: " + PAGE + " has no plain track at 0x104");

        final Locomotive train = model.getLocByName(model.getLocList().get(0));

        try
        {
            Files.write(file, text.replace(square, "").getBytes(StandardCharsets.UTF_8));

            syncTheLocomotive(train);

            out.check(train);
        }
        finally
        {
            Files.write(file, asItWas);

            syncTheLocomotive(train);
        }

        back.check(train);
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
