package ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.Mouse;
import support.Reflect;

/**
 * While one train is sent by hand, the track diagram's right-click menu over another standing train offers that train's
 * destinations, as its Auto tab card does (FR-187), and no setup edit; on the frozen copy of Adam's railway, through the
 * real right-click.
 */
public class testAStandingTrainIsSentWhileAnotherRuns
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static boolean echoWas;

    /** The train sent out by the claim, its journey and its thread. */
    private Locomotive out;
    private List<Edge> outOn;
    private Thread driving;

    /**
     * A window of its own for each claim: a journey cut short leaves the railway as no rebuild puts back.  The power on,
     * which a hand send asks.
     */
    @BeforeMethod
    public void aRailwayAtRest() throws Exception
    {
        AskingWindows.Railway opened = AskingWindows.openRailway("live-snapshot", null);

        sandbox = opened.sandbox;
        model = opened.model;
        ui = opened.ui;

        echoWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        Components.settle();

        session = ui.getAutonomySession();

        assertNotNull(session, "no autonomy setup on this railway");

        model.go();

        assertTrue(model.waitForPowerState(true, 20000), "precondition: the power did not come on");
    }

    /** Every flag a claim raised put down, the trains stopped and the window closed. */
    @AfterMethod(alwaysRun = true)
    public void closeTheRailway() throws Exception
    {
        try
        {
            Mouse.closeMenus();

            AskingWindows.closeEveryDialog();

            if (driving != null) driving.interrupt();

            if (ui != null)
            {
                railway().stopLocomotives();

                final TrainControlUI closing = ui;

                javax.swing.SwingUtilities.invokeAndWait(closing::dispose);
            }
        }
        finally
        {
            MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

            driving = null;
            out = null;
            outOn = null;
            ui = null;

            if (sandbox != null) sandbox.close();

            sandbox = null;
        }
    }

    /**
     * During a hand send of one train, the menu over another standing train lists that train's destinations, the same
     * ones its Auto tab card offers.
     *
     * MUTATION: have `gatherPathOptions` answer null whenever `isAutonomyBusy()`, and this fails.
     */
    @Test(timeOut = 300000)
    public void testTheMenuOverAStandingTrainListsItsDestinations() throws Exception
    {
        sendOneOut(null);

        Point standing = aStandingTrain(false);
        Locomotive train = standing.getCurrentLocomotive();

        JPopupMenu menu = menuOver(standing);

        assertTrue(AskingWindows.hasItem(menu, I18n.t("autolayout.ui.menuStopAutonomyGracefully")),
            "precondition: this is not the menu shown while a train runs");

        List<String> listed = destinations(menu);

        Mouse.closeMenus();

        assertFalse(listed.isEmpty(), "while " + out.getName() + " is sent by hand, the menu over " + train.getName()
            + " lists none of its destinations");

        // The card searched again now, as the menu did: a card drawn before the train set off shows that minute's paths
        ui.repaintAutoLocList(false);

        assertTrue(Components.waitFor(() -> listed.equals(cardOffers(train)), 30000), "the menu over " + train.getName()
            + " lists " + listed + ", its Auto tab card " + cardOffers(train));
    }

    /**
     * Choosing one of them sends that train, and the train already out stays on its journey.
     *
     * MUTATION: have `sendATrainByHand` refuse while any train is out (`railway.isRunning()`), and this fails.
     */
    @Test(timeOut = 300000)
    public void testChoosingOneSendsItAndTheOtherStaysOnItsWay() throws Exception
    {
        sendOneOut(null);

        Object[] found = aTrainWithAWayClearOf(outOn);

        Point standing = (Point) found[0];
        Locomotive train = standing.getCurrentLocomotive();
        String to = (String) found[1];

        try (Answering answering = Answering.start(message -> null))
        {
            Mouse.choose(AskingWindows.itemOf(menuOver(standing), "-> " + to));

            assertTrue(Components.waitFor(() -> railway().isAlreadyUnderway(train), 30000), "choosing -> " + to
                + " did not send " + train.getName() + ": " + answering.asked());
        }

        assertEquals(railway().getActiveLocomotives().get(out), outOn, out.getName() + " left its journey when "
            + train.getName() + " was sent");
    }

    /**
     * The train out has no destinations on its own menu, and a standing train none while autonomy runs or Return Home
     * plans: those still own the railway.
     *
     * MUTATION: drop `!ui.stagingFlowActive` from `RunGates.mayBeSentByHandNow`, and this fails.
     */
    @Test(timeOut = 300000)
    public void testNoneWhileTheTrainIsOutOrARunOwnsTheRailway() throws Exception
    {
        Point from = sendOneOut(null);

        assertTrue(Components.waitFor(() -> out.equals(from.getCurrentLocomotive()), 10000),
            "precondition: " + out.getName() + " is not on the Point it set off from");

        assertNotNull(squareOf(from), "precondition: " + out.getName() + " set off from no station");

        assertEquals(destinations(menuOver(from)), new ArrayList<String>(), "the menu over " + out.getName()
            + ", which is out, offers it destinations");

        Mouse.closeMenus();

        Point standing = aStandingTrain(false);

        for (String flag : new String[] {"autonomy", "Return Home"})
        {
            raise(flag, true);

            try
            {
                assertEquals(destinations(menuOver(standing)), new ArrayList<String>(), "the menu over "
                    + standing.getCurrentLocomotive().getName() + " offers destinations while " + flag + " runs");
            }
            finally
            {
                Mouse.closeMenus();

                raise(flag, false);
            }
        }
    }

    /**
     * A destination offered before autonomy or Return Home began sends nothing when chosen after, and says to wait: from
     * a menu opened at rest, and from one opened during a hand send.
     *
     * MUTATION: drop the `mayBeSentByHandNow` question from `sendATrainByHand`, and this fails.
     */
    @Test(timeOut = 300000)
    public void testADestinationChosenAfterARunBeganSendsNothing() throws Exception
    {
        Point standing = aStandingTrain(true);
        Locomotive train = standing.getCurrentLocomotive();

        JMenuItem atRest = aDestination(menuOver(standing));

        refusedAfter("Return Home", atRest, train);

        sendOneOut(train.getName());

        JMenuItem duringTheSend = aDestination(menuOver(standing));

        refusedAfter("autonomy", duringTheSend, train);
    }

    /**
     * The menu over a standing train during a hand send offers no setup edit (FR-129): Is Active, which pauses the train
     * alone, and the read-only windows only.
     *
     * MUTATION: add the facing items (`addAutonomyFacingItems`) to the running menu whatever holds the setup, and this fails.
     */
    @Test(timeOut = 300000)
    public void testNoSetupEditWhileATrainIsSentByHand() throws Exception
    {
        Point standing = aStandingTrain(true);
        TileKey square = squareOf(standing);
        String train = standing.getCurrentLocomotive().getName();

        JPopupMenu facing = new JPopupMenu();

        Components.onTheEventThread(() -> { ui.addAutonomyFacingItems(facing, square); return null; });

        List<String> setupEdits = new ArrayList<>(texts(facing));

        setupEdits.addAll(java.util.Arrays.asList(I18n.f("layout.ui.menuRemoveLocomotive", train),
            I18n.t("autosetup.ui.menuSetLength"), I18n.t("autosetup.ui.menuOpenFullEditor"),
            I18n.t("autosetup.ui.menuBulkTools")));

        if (ui.getActiveLoc() != null && !train.equals(ui.getActiveLoc().getName()))
        {
            setupEdits.add(I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName()));
        }

        List<String> atRest = texts(menuOver(standing));

        Mouse.closeMenus();

        assertTrue(atRest.containsAll(setupEdits), "precondition: the menu at rest lacks one of " + setupEdits + ": "
            + atRest);

        sendOneOut(train);

        List<String> running = texts(menuOver(standing));

        for (String edit : setupEdits)
        {
            assertFalse(running.contains(edit), "the menu over " + train + " offers \"" + edit + "\" while "
                + out.getName() + " is sent by hand: " + running);
        }
    }

    // ------------------------------------------------------------------------------------------------------- helpers

    private static Layout railway()
    {
        return model.getAutoLayout();
    }

    /** Sends a train other than this one along a path, as the import door's claim does; the Point it set off from. */
    private Point sendOneOut(String notThis) throws Exception
    {
        Object[] dispatched = regression.testTheImportDoorReadsAnOldFile.dispatchATrain(ui, notThis);

        out = model.getLocByName((String) dispatched[0]);
        outOn = railway().getActiveLocomotives().get(out);
        driving = (Thread) dispatched[3];

        assertNotNull(outOn, "precondition: " + out.getName() + " is not on its journey");

        return railway().getPoint((String) dispatched[1]);
    }

    /**
     * The trains standing on a station, not out, with somewhere to go: the Points the menu over their squares is about.
     */
    private List<Point> standingTrains()
    {
        List<Point> found = new ArrayList<>();

        for (Point point : railway().getPoints())
        {
            Locomotive train = point.getCurrentLocomotive();

            if (train == null || !point.isDestination() || train.equals(out) || railway().isAlreadyUnderway(train)
                || squareOf(point) == null || !session.getStore().isStation(squareOf(point))
                || railway().getPossiblePaths(train, true).isEmpty()) continue;

            if (Components.onTheEventThread(() -> point.equals(ui.getAutonomyPointForTile(squareOf(point)))))
            {
                found.add(point);
            }
        }

        return found;
    }

    /** The first of them. */
    private Point aStandingTrain(boolean atRest)
    {
        List<Point> found = standingTrains();

        assertFalse(found.isEmpty(), "precondition: no other train stands on a station" + (atRest ? "" : " during the run"));

        return found.get(0);
    }

    /** A standing train with a destination whose way shares no Point with this journey: its Point and where to. */
    @SuppressWarnings("unchecked")
    private Object[] aTrainWithAWayClearOf(List<Edge> journey) throws Exception
    {
        java.util.Set<Point> taken = new java.util.HashSet<>();

        for (Edge e : journey)
        {
            taken.add(e.getStart());
            taken.add(e.getEnd());
        }

        java.lang.reflect.Method gather = Reflect.method(Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu"),
            "gatherPathOptions", TrainControlUI.class, Point.class);
        java.lang.reflect.Method mayTake = Reflect.method(TrainControlUI.class, "whyThisPathCannotBeTaken", Layout.class,
            List.class, Locomotive.class);

        for (Point point : standingTrains())
        {
            Locomotive train = point.getCurrentLocomotive();

            Object options = gather.invoke(null, ui, point);

            if (options == null) continue;

            for (List<Edge> path : (List<List<Edge>>) Reflect.get(options.getClass(), options, "shown"))
            {
                boolean clear = mayTake.invoke(null, railway(), path, train) == null;

                for (Edge e : path) clear &= e.getStart() == point || !taken.contains(e.getStart()) && !taken.contains(e.getEnd());

                if (clear) return new Object[] {point, session.getStationIndex().describe(path.get(path.size() - 1).getEnd())};
            }
        }

        throw new AssertionError("precondition: no standing train has a way clear of " + out.getName() + "'s");
    }

    private static TileKey squareOf(Point point)
    {
        return session.getStationIndex().squareOf(point.getName());
    }

    /** The right-click menu over this train's square in the main window, opened as the mouse opens it. */
    private static JPopupMenu menuOver(Point standing) throws Exception
    {
        AskingWindows.showThePageOf(ui, squareOf(standing));

        return AskingWindows.menuOn(ui, ui, squareOf(standing));
    }

    /** The destinations a menu lists, top level and More Destinations, by name. */
    private static List<String> destinations(JPopupMenu menu)
    {
        List<String> names = new ArrayList<>();

        for (String text : texts(menu)) if (text.startsWith("-> ")) names.add(text.substring(3));

        java.util.Collections.sort(names);

        return names;
    }

    /** Every item's text, submenus included. */
    private static List<String> texts(java.awt.Container menu)
    {
        List<String> out = new ArrayList<>();

        for (JMenuItem item : Components.all(menu, JMenuItem.class, null)) out.add(item.getText());

        return out;
    }

    /** The first destination a menu lists. */
    private static JMenuItem aDestination(JPopupMenu menu)
    {
        List<String> listed = destinations(menu);

        assertFalse(listed.isEmpty(), "precondition: the menu lists no destination");

        return AskingWindows.itemOf(menu, "-> " + listed.get(0));
    }

    /** The destinations this train's Auto tab card offers, by name, as the menu names them. */
    @SuppressWarnings("unchecked")
    private static List<String> cardOffers(Locomotive train)
    {
        return Components.onTheEventThread(() ->
        {
            List<String> names = new ArrayList<>();

            for (java.awt.Component card : ((javax.swing.JPanel) Reflect.get(TrainControlUI.class, ui, "autoLocPanel"))
                .getComponents())
            {
                Class<?> type = org.traincontrol.gui.autonomy.AutoLocomotiveStatus.class;

                if (!type.isInstance(card) || !train.equals(Reflect.get(type, card, "locomotive"))) continue;

                List<List<Edge>> paths = (List<List<Edge>>) Reflect.get(type, card, "paths");

                if (paths != null) for (List<Edge> path : paths)
                {
                    names.add(session.getStationIndex().describe(path.get(path.size() - 1).getEnd()));
                }
            }

            java.util.Collections.sort(names);

            return names;
        });
    }

    /** Raises or lowers what autonomy, or Return Home, sets as it begins. */
    private static void raise(String run, boolean up) throws Exception
    {
        if ("autonomy".equals(run))
        {
            Reflect.set(Layout.class, railway(), "running", up);
        }
        else
        {
            Components.onTheEventThread(() -> { Reflect.set(TrainControlUI.class, ui, "stagingFlowActive", up); return null; });
        }
    }

    /** Chooses an item offered before this run began, once it has: it says to wait, and the train is not sent. */
    private static void refusedAfter(String run, JMenuItem item, Locomotive train) throws Exception
    {
        raise(run, true);

        try (Answering answering = Answering.start(message -> null))
        {
            Mouse.choose(item);

            String wait = I18n.t("autolayout.ui.errorWaitForActiveLocomotivesToStop");

            assertTrue(Components.waitFor(() -> answering.asked().contains(wait), 10000), item.getText() + ", offered"
                + " before " + run + " began and chosen after, did not say to wait: " + answering.asked());

            Components.settle();

            assertFalse(railway().isAlreadyUnderway(train), item.getText() + ", chosen after " + run + " began, sent "
                + train.getName());
        }
        finally
        {
            raise(run, false);
        }
    }
}
