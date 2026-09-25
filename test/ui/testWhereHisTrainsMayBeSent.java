package ui;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Where a train on the frozen copy of Adam's railway may be sent, read where the steps read it: the track diagram's
 * right-click list of destinations, and the autonomy editor's Why not Moving? (Adam, 2026-09-25: automated tests
 * supersede the MTs they answer).
 *
 * MT-517 is a train that cannot reverse and a terminus; MT-584 a square closed while a train stands on the one it
 * watches, set through the editor's own Unavailable While Occupied window; MT-495 a train turned at Tunnel and another's
 * tail across its way out.  His own trains, from the run's copy of his locomotive data.
 *
 * @author Adam
 */
public class testWhereHisTrainsMayBeSent
{
    private static final String HIS_TRAIN = "75 407 DB";
    private static final String CANNOT_REVERSE = "EN57-947";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;

    /** Every train moved here, and the length it had, put back after. */
    private static final Map<Locomotive, Integer> lengths = new java.util.LinkedHashMap<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the diagram's doors belong to a window, and a window needs a display");
        }

        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111).
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        pump();
        pump();

        session = ui.getAutonomySession();

        if (session == null) throw new SkipException("no autonomy setup on this railway");

        assertNotNull(railway(), "the frozen railway built no autonomy layout");

        TailCrossedPrompt.answerForTests(TailCrossedPrompt.NOT_KNOWN);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            TailCrossedPrompt.answerForTests(null);

            for (Map.Entry<Locomotive, Integer> was : lengths.entrySet()) was.getKey().setTrainLength(was.getValue());
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** Nothing standing about from the claim before, and nothing left open. */
    @AfterMethod(alwaysRun = true)
    public void clearTheRailway() throws Exception
    {
        closeEveryDialog();

        SwingUtilities.invokeAndWait(() ->
        {
            for (Point point : railway().getPoints())
            {
                if (point.getCurrentLocomotive() != null) railway().moveLocomotive(null, point.getName(), true);
            }
        });
    }

    /**
     * EN57-947, which cannot reverse, at BottomSecondary: Why not Moving? on Manual lists BottomMainC under the stations
     * it cannot be sent to right now, saying a terminus is not allowed because it is not reversible - and the right-click
     * list does not offer BottomMainC (MT-517).
     *
     * MUTATION: offer a terminus to a train that cannot reverse, or drop its reason from the Manual answer, and this
     * fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testATerminusIsRefusedToATrainThatCannotReverse() throws Exception
    {
        clearTheRailway();

        final Locomotive en57 = train(CANNOT_REVERSE);

        // THE ENTRY'S PREMISE, MADE TRUE: in his locomotive data of 2026-09-25 EN57-947 is marked as able to reverse, so
        // it is marked unable here, as the locomotive window marks it, and put back after.
        final boolean couldReverse = en57.isReversible();

        en57.setReversible(false);

        try
        {
            aTerminusIsRefusedTo(en57);
        }
        finally
        {
            en57.setReversible(couldReverse);
        }
    }

    private static void aTerminusIsRefusedTo(Locomotive en57) throws Exception
    {
        assertFalse(en57.isReversible(), "precondition: " + CANNOT_REVERSE + " can reverse");

        TileKey secondary = square("BottomSecondary");

        // STEP 1.
        standOn(en57, secondary, null);

        // STEP 2: Path Type Manual, Why Not Moving?, and a click on it.
        String said = whyNotMoving(secondary, true);

        String reason = I18n.f("autolayout.errorTerminusNotAllowedForNonReversibleLoc", CANNOT_REVERSE);

        String header = I18n.t("autolayout.ui.whyHeaderByHand");

        header = header.substring(0, header.indexOf('(')).trim();

        int under = said.indexOf(header);

        assertTrue(under >= 0, "Why not Moving? on Manual has no \"" + header + "\" list (MT-517): " + said);

        java.util.regex.Matcher line = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote("BottomMainC")
            + "[^:]*: " + java.util.regex.Pattern.quote(reason)).matcher(said);

        assertTrue(line.find(under), "Why not Moving? on Manual does not list BottomMainC under \"" + header + "\" as a"
            + " terminus " + CANNOT_REVERSE + " cannot reverse at (MT-517): " + said);

        String barred = I18n.t("autolayout.ui.whyHeaderBarred");

        int next = said.indexOf(barred.substring(0, barred.indexOf('(')).trim(), under);

        assertTrue(next < 0 || line.start() < next, "BottomMainC's terminus reason is listed under another heading"
            + " (MT-517): " + said);

        // STEP 3.
        assertFalse(offered(secondary).contains("BottomMainC"), "the right-click menu offers BottomMainC to "
            + CANNOT_REVERSE + ", which cannot reverse there (MT-517): " + offered(secondary));
    }

    /**
     * BottomMainAPre, set Unavailable While Occupied watching TunnelLeftPark in the editor's own window: with a train
     * standing on TunnelLeftPark, 75 407 DB at Tunnel facing BottomMainA is not offered BottomMainA and Why not Moving?
     * says BottomMainAPre is not available while TunnelLeftPark is occupied; with the train gone, it is offered again
     * (MT-584).
     *
     * MUTATION: let a train standing on the watched square hold nothing back, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testATrainOnTheWatchedSquareClosesBottomMainAPre() throws Exception
    {
        clearTheRailway();

        TileKey pre = square("BottomMainAPre");
        TileKey leftPark = square("TunnelLeftPark");
        TileKey tunnel = square("Tunnel");

        Locomotive his = train(HIS_TRAIN);
        Locomotive other = anotherTrain(HIS_TRAIN, CANNOT_REVERSE);

        List<TileKey> before = new ArrayList<>(session.getStore().getBlockingPoints(pre));

        try
        {
            // STEP 2: Advanced Parameters > Unavailable While Occupied, TunnelLeftPark ticked, OK; the editor closed
            // saving it.
            watch(pre, leftPark);

            assertTrue(session.getStore().getBlockingPoints(pre).contains(leftPark), "precondition: the Unavailable While"
                + " Occupied window did not record TunnelLeftPark for BottomMainAPre");

            // STEP 3.
            standOn(other, leftPark, null);

            standOn(his, tunnel, session.facingsFor(tunnel).get("Tunnel (southbound)"));

            // STEP 4.
            String blocked = I18n.f("autolayout.errorDestinationBlockedByPoint", "BottomMainAPre", "TunnelLeftPark");

            assertFalse(offered(tunnel).contains("BottomMainA"), "with a train standing on TunnelLeftPark, the right-click"
                + " menu offers " + HIS_TRAIN + " BottomMainA through BottomMainAPre (MT-584): " + offered(tunnel));

            String said = whyNotMoving(tunnel, false);

            assertTrue(said.contains(blocked.replaceAll("\\s+", " ")), "Why not Moving? does not say \"" + blocked
                + "\" (MT-584): " + said);

            // STEP 5: the train taken off TunnelLeftPark.
            SwingUtilities.invokeAndWait(() ->
            {
                Point at = railway().getLocomotiveLocation(other);

                if (at != null) railway().moveLocomotive(null, at.getName(), false);
            });

            pump();

            List<String> again = offered(tunnel);

            assertTrue(again.contains("BottomMainA"), "with TunnelLeftPark clear, BottomMainA is not offered again"
                + " (MT-584): " + again + "; Why not Moving? says: " + whyNotMoving(tunnel, false));
        }
        finally
        {
            SwingUtilities.invokeAndWait(() ->
            {
                session.getStore().setBlockingPoints(pre, before);
                ui.rebuildRunningLayoutFromSetup();
            });

            pump();
        }
    }

    /**
     * A train turned at Tunnel - come in from the south, facing south - is not offered any way south through the points a
     * three-unit train in TunnelRightPark lies across; with that train one unit long, it is (MT-495, as its comment of
     * 2026-09-24 says to read it: the right-click on the turned train).
     *
     * Read off the right-click's own list of routes - every path it offers, in the menu and under its More item -
     * because BottomMainAPre, the square the way south runs through, is no station, and so no destination is named after
     * it.
     *
     * MUTATION: stop the turned train's way out at its own tail, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testATurnedTrainIsNotOfferedTheWayAcrossAParkedTail() throws Exception
    {
        clearTheRailway();

        Locomotive turned = anotherTrain(HIS_TRAIN, CANNOT_REVERSE);
        Locomotive parked = anotherTrain(turned.getName(), HIS_TRAIN, CANNOT_REVERSE);

        TileKey tunnel = square("Tunnel");

        final Point atTunnel = railway().getPoint("Tunnel (southbound)");

        assertNotNull(atTunnel, "precondition: the frozen railway has no southbound copy of Tunnel");

        // STEP 2: came in from the south and reversed, so it faces south with its tail on its way out.
        remember(turned);

        turned.setTrainLength(1);

        SwingUtilities.invokeAndWait(() ->
        {
            atTunnel.setLocomotive(turned);
            atTunnel.setArrivedFrom("S");
        });

        // STEP 1: a train in TunnelRightPark, come in over the points at column 7.
        Point[] park = theParkComeInOverThePoints(atTunnel);

        assertNotNull(park, "precondition: no copy of TunnelRightPark is come into over the track Tunnel's way south"
            + " runs over");

        remember(parked);

        // THE CONTROL (STEP 4): one unit, clear of the points - the way south is offered.
        parkATrainOf(parked, park, 1);

        assertTrue(anOfferedPathGoesThrough(tunnel, "BottomMainAPre"), "control: with the train in TunnelRightPark one"
            + " unit long, no way south through BottomMainAPre is offered to the turned train, so the claim below is not"
            + " about a tail: " + offered(tunnel));

        // STEP 3: three units, lying back across the points.
        parkATrainOf(parked, park, 3);

        assertFalse(anOfferedPathGoesThrough(tunnel, "BottomMainAPre"), "the right-click offers the train turned at"
            + " Tunnel a way south through the points a three-unit train in TunnelRightPark lies across (MT-495): "
            + offered(tunnel));
    }

    /**
     * On 1 - Main with three of his trains standing on it, Why not Moving? outlines every square with a train on it and
     * no other while it waits; a click on one answers, and the outlines that marked where to click go (MT-570, step 2 as
     * its comment of 2026-09-24 says it more exactly).
     *
     * Asked of every square of the page, from the running railway, which is where the outlines are read since that
     * entry's third comment.
     *
     * MUTATION: outline a square without a train, miss one with a train, or keep the waiting outlines after the click,
     * and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testWhyNotMovingOutlinesHisTrainsWhileItWaits() throws Exception
    {
        clearTheRailway();

        final String page = "1 - Main";

        // THREE TRAINS ON THREE STATIONS OF THE PAGE.
        List<TileKey> stations = new ArrayList<>();

        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (page.equals(key.getPage()) && session.getStore().isStation(key)) stations.add(key);
        }

        stations.sort(java.util.Comparator.comparing(TileKey::toString));

        java.util.Set<TileKey> withTrains = new java.util.LinkedHashSet<>();

        List<String> skip = new ArrayList<>();

        for (TileKey station : stations)
        {
            if (withTrains.size() == 3) break;

            boolean placeable = false;

            for (String copy : session.facingsFor(station).keySet())
            {
                Point point = railway().getPoint(copy);

                if (point != null && point.isDestination()) placeable = true;
            }

            if (!placeable) continue;

            Locomotive loc = anotherTrain(skip.toArray(new String[0]));

            skip.add(loc.getName());

            standOn(loc, station, null);

            withTrains.add(station);
        }

        assertEquals(withTrains.size(), 3, "precondition: three trains could not be stood on " + page);

        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, page, () -> { });

        panel.setRunningLayoutSource(() -> model.getAutoLayout());
        panel.setLayoutSource(() -> model.getAutoLayout());

        java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("whyButton");

        field.setAccessible(true);

        final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field.get(panel);

        // STEP 1.
        SwingUtilities.invokeAndWait(why::doClick);

        List<String> wrong = new ArrayList<>();

        int asked = 0;

        for (TileKey tile : session.getGraph().getTiles().keySet())
        {
            if (!page.equals(tile.getPage())) continue;

            asked++;

            final boolean[] outlined = new boolean[1];

            SwingUtilities.invokeAndWait(() ->
            {
                org.traincontrol.automationui.TileAnnotation annotation = panel.annotationFor(tile);

                outlined[0] = annotation != null && annotation.isSelected();
            });

            if (outlined[0] != withTrains.contains(tile)) wrong.add(tile + (outlined[0] ? " outlined" : " not outlined"));
        }

        assertTrue(asked > withTrains.size(), "precondition: " + page + " has no squares without a train to ask about");

        assertTrue(wrong.isEmpty(), "while Why not Moving? waits, the outlines are not exactly the squares with trains on"
            + " them (MT-570 step 1) - trains on " + withTrains + ": " + wrong);

        // STEP 2: a click on one.
        final TileKey clicked = withTrains.iterator().next();

        SwingUtilities.invokeAndWait(() -> panel.tileClicked(clicked, session.getGraph().getTiles().get(clicked), false));

        Method waitsOn = AutonomyEditorPanel.class.getDeclaredMethod("whyWaitsOn", TileKey.class);

        waitsOn.setAccessible(true);

        for (TileKey tile : withTrains)
        {
            assertFalse((Boolean) waitsOn.invoke(panel, tile), "after a click, " + tile + " is still outlined as"
                + " somewhere to click (MT-570 step 2)");
        }

        String working = I18n.t("autolayout.ui.whyWorking");

        String said = hintOf(panel);

        for (long end = System.currentTimeMillis() + 30000; (said == null || said.contains(working))
            && System.currentTimeMillis() < end; )
        {
            Thread.sleep(100);

            pump();

            said = hintOf(panel);
        }

        assertTrue(said != null && !said.contains(working) && !said.equals(I18n.t("autosetup.ui.promptWhy").trim()),
            "after the click no answer was drawn (MT-570 step 2): " + said);
    }

    // ---------------------------------------------------------------- the doors

    /** The editor's Unavailable While Occupied window on this square, with one square ticked and OK; then saved. */
    private static void watch(TileKey square, TileKey watched) throws Exception
    {
        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, square.getPage(), () -> { });

        String pattern = I18n.t("autolayout.ui.menuBlockedByPoints");

        final String prefix = pattern.substring(0, pattern.indexOf('(')).trim();

        final javax.swing.JMenuItem[] item = new javax.swing.JMenuItem[1];

        SwingUtilities.invokeAndWait(() -> item[0] = itemStarting(panel.buildTileMenu(square, null), prefix));

        assertNotNull(item[0], "the right-click menu on " + name(square) + " has no Unavailable While Occupied item");

        SwingUtilities.invokeLater(item[0]::doClick);

        String title = I18n.t("autosetup.ui.menuBlockedByPointsTitle");

        javax.swing.JDialog dialog = awaitDialogTitled(title);

        final String label = name(watched);

        final javax.swing.JCheckBox[] box = new javax.swing.JCheckBox[1];

        final javax.swing.JOptionPane[] pane = new javax.swing.JOptionPane[1];

        SwingUtilities.invokeAndWait(() ->
        {
            box[0] = checkBoxIn(dialog.getContentPane(), label);
            pane[0] = find(dialog.getContentPane(), javax.swing.JOptionPane.class);
        });

        assertNotNull(box[0], "the Unavailable While Occupied window lists no " + label);

        SwingUtilities.invokeAndWait(() ->
        {
            box[0].setSelected(true);

            pane[0].setValue(javax.swing.UIManager.getString("OptionPane.okButtonText"));
        });

        awaitNoDialogTitled(title);

        pump();

        // CLOSED SAVING THE CHANGE: the running railway rebuilt from the setup.
        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());

        pump();
    }

    /** The right-click menu on this square, as the diagram builds it; the paths it offers. */
    @SuppressWarnings("unchecked")
    private static List<List<Edge>> offeredPaths(TileKey square) throws Exception
    {
        final Class<?> menuClass = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

        final Method gather = menuClass.getDeclaredMethod("gatherPathOptions", TrainControlUI.class, Point.class);

        gather.setAccessible(true);

        final Point[] standing = new Point[1];

        SwingUtilities.invokeAndWait(() -> standing[0] = ui.getAutonomyPointForTile(square));

        Object options = gather.invoke(null, ui, standing[0]);

        List<List<Edge>> out = new ArrayList<>();

        if (options == null) return out;

        for (String field : new String[] {"shown", "other"})
        {
            java.lang.reflect.Field list = options.getClass().getDeclaredField(field);

            list.setAccessible(true);

            Object paths = list.get(options);

            if (paths != null) out.addAll((List<List<Edge>>) paths);
        }

        return out;
    }

    /** The destinations the right-click menu on this square offers, by name, as its items read. */
    private static List<String> offered(TileKey square) throws Exception
    {
        final Class<?> menuClass = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

        final Method gather = menuClass.getDeclaredMethod("gatherPathOptions", TrainControlUI.class, Point.class);

        gather.setAccessible(true);

        final java.lang.reflect.Constructor<?> make = menuClass.getDeclaredConstructor(TrainControlUI.class,
            TileKey.class, TileKey.class, Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu$PathOptions"));

        make.setAccessible(true);

        final Point[] standing = new Point[1];

        SwingUtilities.invokeAndWait(() -> standing[0] = ui.getAutonomyPointForTile(square));

        final Object options = gather.invoke(null, ui, standing[0]);

        final List<String> names = new ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                collectDestinations((javax.swing.JPopupMenu) make.newInstance(ui, square, square, options), names);
            }
            catch (ReflectiveOperationException failed)
            {
                throw new IllegalStateException(failed);
            }
        });

        return names;
    }

    private static void collectDestinations(java.awt.Container menu, List<String> into)
    {
        java.awt.Component[] children = menu instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) menu).getMenuComponents() : menu.getComponents();

        for (java.awt.Component child : children)
        {
            if (child instanceof javax.swing.JMenu)
            {
                collectDestinations((javax.swing.JMenu) child, into);
            }
            else if (child instanceof javax.swing.JMenuItem && ((javax.swing.JMenuItem) child).getText() != null
                && ((javax.swing.JMenuItem) child).getText().startsWith("-> "))
            {
                into.add(((javax.swing.JMenuItem) child).getText().substring(3).trim());
            }
        }
    }

    private static boolean anOfferedPathGoesThrough(TileKey square, String through) throws Exception
    {
        for (List<Edge> path : offeredPaths(square))
        {
            for (Edge edge : path)
            {
                if (edge.getEnd().getName().startsWith(through) || edge.getStart().getName().startsWith(through)) return true;
            }
        }

        return false;
    }

    /** Presses Why not Moving? in the autonomy editor on this square's page - on Manual where asked - and clicks it. */
    private static String whyNotMoving(TileKey square, boolean manual) throws Exception
    {
        final AutonomyEditorPanel panel = new AutonomyEditorPanel(session, square.getPage(), () -> { });

        panel.setRunningLayoutSource(() -> model.getAutoLayout());
        panel.setLayoutSource(() -> model.getAutoLayout());

        if (manual)
        {
            java.lang.reflect.Field type = AutonomyEditorPanel.class.getDeclaredField("pathTypeManual");

            type.setAccessible(true);

            final javax.swing.AbstractButton byHand = (javax.swing.AbstractButton) type.get(panel);

            SwingUtilities.invokeAndWait(byHand::doClick);
        }

        java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("whyButton");

        field.setAccessible(true);

        final javax.swing.AbstractButton why = (javax.swing.AbstractButton) field.get(panel);

        SwingUtilities.invokeAndWait(why::doClick);

        SwingUtilities.invokeAndWait(() -> panel.tileClicked(square, session.getGraph().getTiles().get(square), false));

        String working = I18n.t("autolayout.ui.whyWorking");

        String said = hintOf(panel);

        for (long end = System.currentTimeMillis() + 30000; (said == null || said.contains(working))
            && System.currentTimeMillis() < end; )
        {
            Thread.sleep(100);

            pump();

            said = hintOf(panel);
        }

        return String.valueOf(said);
    }

    // ---------------------------------------------------------------- the railway

    /** Stands this train on a copy of the square - facing this way, where one is given - as a hand drive leaves it. */
    private static void standOn(Locomotive loc, TileKey square, Side heading) throws Exception
    {
        String copy = null;

        for (Map.Entry<String, Side> each : session.facingsFor(square).entrySet())
        {
            Point point = railway().getPoint(each.getKey());

            if (point == null || (heading == null && !point.isDestination())) continue;

            if (heading == null || each.getValue() == heading)
            {
                copy = each.getKey();

                if (heading == null) break;
            }
        }

        assertNotNull(copy, "precondition: " + name(square) + " has no copy to stand " + loc.getName() + " on facing "
            + heading + ": " + session.facingsFor(square));

        final String onto = copy;

        SwingUtilities.invokeAndWait(() ->
        {
            Point was = railway().getLocomotiveLocation(loc);

            if (was != null) railway().moveLocomotive(null, was.getName(), false);

            railway().moveLocomotive(loc.getName(), onto, false, true);
        });

        pump();

        Point at = railway().getLocomotiveLocation(loc);

        assertTrue(at != null && session.facingsFor(square).containsKey(at.getName()), "precondition: " + loc.getName()
            + " could not be stood on " + onto);
    }

    /** The copy of TunnelRightPark come into over the track Tunnel's way south runs over, and that way in. */
    private static Point[] theParkComeInOverThePoints(Point atTunnel)
    {
        Edge wayOut = null;

        for (Edge out : railway().getNeighbors(atTunnel))
        {
            if (out.getEnd().getName().startsWith("BottomMainAPre")) wayOut = out;
        }

        if (wayOut == null) return null;

        for (String name : session.getStationIndex().pointNamesAt(square("TunnelRightPark")))
        {
            Point copy = railway().getPoint(name);

            if (copy == null || !copy.isDestination()) continue;

            for (Edge in : railway().getNeighborsAndIncoming(copy))
            {
                if (in.getEnd() != copy) continue;

                List<String> shared = new ArrayList<>(in.getPlaceIds());

                shared.retainAll(wayOut.getPlaceIds());

                if (!shared.isEmpty()) return new Point[] {copy, null, null};
            }
        }

        return null;
    }

    /** The train in TunnelRightPark, come in over the points, as long as given. */
    private static void parkATrainOf(Locomotive parked, Point[] park, int length) throws Exception
    {
        final Point copy = park[0];

        Edge approach = null;

        for (Edge in : railway().getNeighborsAndIncoming(copy))
        {
            if (in.getEnd() != copy) continue;

            for (Edge out : railway().getNeighbors(railway().getPoint("Tunnel (southbound)")))
            {
                if (!out.getEnd().getName().startsWith("BottomMainAPre")) continue;

                List<String> shared = new ArrayList<>(in.getPlaceIds());

                shared.retainAll(out.getPlaceIds());

                if (!shared.isEmpty()) approach = in;
            }
        }

        final Edge cameIn = approach;

        parked.setTrainLength(length);

        SwingUtilities.invokeAndWait(() ->
        {
            copy.setLocomotive(parked);
            copy.setArrivedFrom(railway().entrySideOf(cameIn, copy));
            copy.setArrivedAlong(java.util.Arrays.asList(cameIn));
        });

        pump();
    }

    private static void remember(Locomotive loc)
    {
        if (!lengths.containsKey(loc)) lengths.put(loc, loc.getTrainLength());
    }

    private static Locomotive train(String name)
    {
        Locomotive loc = model.getLocByName(name);

        assertNotNull(loc, "precondition: this data has no " + name + ", the train the steps use");

        return loc;
    }

    /** A train of his that is none of these, by name, the first in his list. */
    private static Locomotive anotherTrain(String... not)
    {
        List<String> skip = java.util.Arrays.asList(not);

        for (String name : model.getLocList())
        {
            if (!skip.contains(name)) return model.getLocByName(name);
        }

        throw new SkipException("this data has too few trains");
    }

    /** The railway as the model has it NOW; a rebuild replaces it. */
    private static Layout railway()
    {
        return model.getAutoLayout();
    }

    /** The square the setup gives this name. */
    private static TileKey square(String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        throw new SkipException("this railway has no " + name);
    }

    private static String name(TileKey square)
    {
        return session.getStore().getPointName(square);
    }

    // ---------------------------------------------------------------- the screen

    private static String hintOf(AutonomyEditorPanel panel) throws Exception
    {
        java.lang.reflect.Field field = AutonomyEditorPanel.class.getDeclaredField("hint");

        field.setAccessible(true);

        final String[] text = new String[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                text[0] = ((javax.swing.JLabel) field.get(panel)).getText();
            }
            catch (IllegalAccessException cannot)
            {
                throw new IllegalStateException(cannot);
            }
        });

        return text[0] == null ? null : text[0].replaceAll("<[^>]*>", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&").replace("&quot;", "\"").replaceAll("\\s+", " ").trim();
    }

    private static javax.swing.JMenuItem itemStarting(java.awt.Container menu, String prefix)
    {
        java.awt.Component[] children = menu instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) menu).getMenuComponents() : menu.getComponents();

        for (java.awt.Component child : children)
        {
            if (child instanceof javax.swing.JMenu)
            {
                javax.swing.JMenuItem found = itemStarting((javax.swing.JMenu) child, prefix);

                if (found != null) return found;
            }
            else if (child instanceof javax.swing.JMenuItem && ((javax.swing.JMenuItem) child).getText() != null
                && ((javax.swing.JMenuItem) child).getText().startsWith(prefix))
            {
                return (javax.swing.JMenuItem) child;
            }
        }

        return null;
    }

    private static javax.swing.JCheckBox checkBoxIn(java.awt.Container container, String text)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JCheckBox && text.equals(((javax.swing.JCheckBox) child).getText()))
            {
                return (javax.swing.JCheckBox) child;
            }

            if (child instanceof java.awt.Container)
            {
                javax.swing.JCheckBox found = checkBoxIn((java.awt.Container) child, text);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static <T> T find(java.awt.Container container, Class<T> type)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (type.isInstance(child)) return type.cast(child);

            if (child instanceof java.awt.Container)
            {
                T found = find((java.awt.Container) child, type);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static javax.swing.JDialog awaitDialogTitled(String title) throws Exception
    {
        long giveUp = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()
                    && title.equals(((javax.swing.JDialog) window).getTitle())) return (javax.swing.JDialog) window;
            }

            Thread.sleep(50);
        }

        fail("no dialog titled \"" + title + "\" appeared");

        return null;
    }

    private static void awaitNoDialogTitled(String title) throws Exception
    {
        long giveUp = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < giveUp)
        {
            boolean showing = false;

            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()
                    && title.equals(((javax.swing.JDialog) window).getTitle())) showing = true;
            }

            if (!showing) return;

            Thread.sleep(50);
        }

        fail("the dialog titled \"" + title + "\" did not close");
    }

    private static void closeEveryDialog() throws Exception
    {
        for (int round = 0; round < 50; round++)
        {
            pump();

            boolean any = false;

            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                final javax.swing.JOptionPane pane = find(((javax.swing.JDialog) window).getContentPane(),
                    javax.swing.JOptionPane.class);

                if (pane == null) continue;

                any = true;

                SwingUtilities.invokeAndWait(() -> pane.setValue(Integer.valueOf(javax.swing.JOptionPane.CLOSED_OPTION)));
            }

            if (!any) return;

            Thread.sleep(100);
        }
    }

    private static void pump() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
