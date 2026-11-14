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
import support.StandInStation;
import support.TestStationAddress;

/**
 * The doors that change what the track diagram does not draw keep the diagram and every cached page: following a link,
 * and a route renumbered, disarmed or deleted, while the route squares follow in place; a locomotive renamed is renamed
 * on every caption, a page window's too; and a sync that reads the pages again, as Sync of one locomotive does from a
 * local folder, still redraws an edit made elsewhere (Adam: "just make sure locomotive renames or remote track diagram
 * changes are properly updated").  On a copy of the frozen railway, syncing with a stand-in station.
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

        try
        {
            if (station != null) station.close();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    @BeforeMethod
    public void showTheMainPage() throws Exception
    {
        Components.select((JComboBox<?>) windowField("LayoutList"), PAGE);

        settle();
    }

    /**
     * A link square shows its page from the cache, as picking the page does, and the page it left stays cached: it
     * drew the page twice, the second time afresh, emptying the cache of every other page.
     *
     * MUTATION: put back `this.repaintLayout()` after the selection in `TrainControlUI.followLink`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testFollowingALinkShowsItsPageFromTheCache() throws Exception
    {
        final LayoutDiagramComponent link = onTheEventThread(() ->
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
        Components.select((JComboBox<?>) windowField("LayoutList"), target);
        settle();
        Components.select((JComboBox<?>) windowField("LayoutList"), PAGE);
        settle();

        final Component left = onTheEventThread(() -> shownPage());
        final JPanel cachedTarget = onTheEventThread(() -> ui.layoutCache.get(cacheKey(target)));

        assertNotNull(cachedTarget, "precondition: " + target + " is not cached after being shown");

        SwingUtilities.invokeAndWait(() -> ui.followLink(link));

        settle();

        assertEquals(onTheEventThread(() -> String.valueOf(((JComboBox<?>) windowField("LayoutList"))
            .getSelectedItem())), target, "the link did not go to " + target);

        assertSame(onTheEventThread(() -> shownPage()), cachedTarget, "the link drew " + target + " afresh instead"
            + " of showing it from the cache");

        assertSame(onTheEventThread(() -> ui.layoutCache.get(cacheKey(PAGE))), left, "following the link threw away"
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

            settle();

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

            settle();

            assertEquals(route.getId(), id, "precondition: the route was not renumbered back");

            assertNotSame(onTheEventThread(() -> shownPage()), kept.shown, "a sync that read the pages again did"
                + " not draw the diagram afresh");

            LayoutLabel now = labelOf(squareNow(square));
            String says = onTheEventThread(() -> now.getToolTipText());

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

            ui.enableOrDisableRoute(route.getName(), false);

            assertTrue(Components.waitFor(() -> model.getRoute(route.getName()) != route, 20000),
                "precondition: disarming did not write the route anew");

            settle();

            kept.assertKept("disarming " + route.getName());

            Route written = model.getRoute(route.getName());

            assertSame(square.getRoute(), written, "precondition: the model left the square on the old route");

            assertTrue(tilesOf((MarklinRoute) written).contains(label), "the square of " + route.getName()
                + " does not show the route written anew running");
        }
        finally
        {
            MarklinRoute now = (MarklinRoute) model.getRoute(route.getName());

            if (now != null)
            {
                now.setLocked(wasLocked);

                if (now.isEnabled() != wasEnabled) model.editRoute(now.getName(), now.getName(), now.getRoute(),
                    now.getS88(), now.getTriggerType(), wasEnabled, now.getConditions());
            }

            rebuildThePage();
        }
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

            settle();

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

            rename(train, renamed);

            assertTrue(captionIn(ui, at).contains("Zq"), "the caption at " + at + " says " + captionIn(ui, at)
                + " after " + name + " was renamed " + renamed);

            assertTrue(captionIn(window, at).contains("Zq"), "the page window's caption at " + at + " says "
                + captionIn(window, at) + " after " + name + " was renamed " + renamed);

            final Component before = onTheEventThread(() -> shownPage());

            syncTheLocomotive(train);

            assertNotSame(onTheEventThread(() -> shownPage()), before, "Sync of " + renamed + " read the pages again"
                + " and did not draw the diagram afresh");

            assertTrue(captionIn(ui, at).contains("Zq"), "after Sync of " + renamed + " the caption at " + at
                + " says " + captionIn(ui, at));
        }
        finally
        {
            rename(train, name);

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
        final Path file = sandbox.getFolder().toPath().resolve("config/gleisbilder/" + PAGE + ".cs2");
        final byte[] asItWas = Files.readAllBytes(file);
        final String text = new String(asItWas, StandardCharsets.UTF_8);

        // A plain track square, taken out
        final String square = "element\r\n .id=0x104\r\n .typ=gerade\r\n .artikel=-1\r\n";
        final String eol = text.contains("\r\n") ? "\r\n" : "\n";
        final String asWritten = square.replace("\r\n", eol);

        assertTrue(text.contains(asWritten), "precondition: " + PAGE + " has no plain track at 0x104");

        final Locomotive train = model.getLocByName(model.getLocList().get(0));

        assertTrue(drawsTheSquare(4, 1), "precondition: the diagram does not draw the square at 4,1");

        try
        {
            Files.write(file, text.replace(asWritten, "").getBytes(StandardCharsets.UTF_8));

            syncTheLocomotive(train);

            assertFalse(drawsTheSquare(4, 1), "the square taken out of " + PAGE + " elsewhere is still drawn after"
                + " Sync of " + train.getName());
        }
        finally
        {
            Files.write(file, asItWas);

            syncTheLocomotive(train);
        }

        assertTrue(drawsTheSquare(4, 1), "the square put back into " + PAGE + " is not drawn after another Sync");
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
            assertSame(onTheEventThread(() -> shownPage()), shown, door + " drew the diagram afresh");

            Map<String, JPanel> now = onTheEventThread(() -> new HashMap<>(ui.layoutCache));

            for (Map.Entry<String, JPanel> page : cached.entrySet())
            {
                assertSame(now.get(page.getKey()), page.getValue(), door + " threw away the cached " + page.getKey());
            }
        }
    }

    /** The diagram shown and every page cached, after another page has been drawn so there is one to keep. */
    private static Kept keptNow() throws Exception
    {
        Components.select((JComboBox<?>) windowField("LayoutList"), "2 - Bottom");
        settle();
        Components.select((JComboBox<?>) windowField("LayoutList"), PAGE);
        settle();

        Map<String, JPanel> cached = onTheEventThread(() -> new HashMap<>(ui.layoutCache));

        assertTrue(cached.size() > 1, "precondition: no other page is cached: " + cached.keySet());

        return new Kept(onTheEventThread(() -> shownPage()), cached);
    }

    /** A route square on the page shown whose route the database has. */
    private static LayoutDiagramComponent aBoundRouteSquare() throws Exception
    {
        LayoutDiagramComponent found = onTheEventThread(() ->
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
        return onTheEventThread(() -> Components.find(shownPage(), LayoutLabel.class, each -> each.getComponent() == c));
    }

    /** The component drawn now where this one was. */
    private static LayoutDiagramComponent squareNow(LayoutDiagramComponent was) throws Exception
    {
        return onTheEventThread(() ->
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

        return onTheEventThread(() -> label.getToolTipText());
    }

    /** Whether the page shown draws something on this square. */
    private static boolean drawsTheSquare(int x, int y) throws Exception
    {
        return onTheEventThread(() -> Components.find(shownPage(), LayoutLabel.class, each -> each.getComponent() != null
            && each.getComponent().getX() == x && each.getComponent().getY() == y) != null);
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

        ui.syncLocomotive(train, done::countDown);

        assertTrue(done.await(60, TimeUnit.SECONDS), "Sync of " + train.getName() + " did not finish");

        settle();
    }

    /** A named, captioned station of the page shown with one train standing at it. */
    private static TileKey aTrainStandingAlone() throws Exception
    {
        final Component page = onTheEventThread(() -> shownPage());

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
        return onTheEventThread(() ->
        {
            StringBuilder said = new StringBuilder();

            for (JLabel label : ui.getLayoutStations(square))
            {
                if (SwingUtilities.isDescendingFrom(label, inside)) said.append(label.getText()).append(' ');
            }

            return said.toString();
        });
    }

    /** Renames through the Locomotive Settings window's Save door. */
    private static void rename(Locomotive train, String name) throws Exception
    {
        final Object actions = windowField("locomotiveActions");

        final java.lang.reflect.Method write = Reflect.method(actions.getClass(), "writeNameAndAddress",
            Component.class, Locomotive.class, String.class, String.class, Locomotive.decoderType.class);

        try (Answering answering = Answering.start(message -> null))
        {
            final Object[] done = new Object[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    done[0] = write.invoke(actions, ui, train, name, "", train.getDecoderType());
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertEquals(done[0], Boolean.TRUE, "the rename to " + name + " was refused: " + answering.asked());
        }

        Components.drain(2);
    }

    /** Draws the page shown afresh and waits for it. */
    private static void rebuildThePage() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.repaintLayout());

        settle();
    }

    /** Lets the grid renderer's jobs and the grids they post finish. */
    private static void settle() throws Exception
    {
        java.util.concurrent.ExecutorService renderer =
            (java.util.concurrent.ExecutorService) windowField("LayoutGridRenderer");

        for (int round = 0; round < 3; round++)
        {
            Components.drain(1);

            renderer.submit(() -> { }).get(30, TimeUnit.SECONDS);

            Components.drain(2);
        }
    }

    private static String cacheKey(String page) throws Exception
    {
        return page + " " + ((JComboBox<?>) windowField("SizeList")).getSelectedItem();
    }

    private static Container shownPage() throws Exception
    {
        JPanel inner = (JPanel) windowField("InnerLayoutPanel");

        return inner.getComponentCount() == 0 ? null : (Container) inner.getComponent(0);
    }

    private static <T> T onTheEventThread(java.util.concurrent.Callable<T> work) throws Exception
    {
        return Components.onTheEventThread(work);
    }

    private static Object windowField(String name) throws Exception
    {
        return Reflect.get(TrainControlUI.class, ui, name);
    }
}
