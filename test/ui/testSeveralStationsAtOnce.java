package ui;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JTextField;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.AutonomySession.WhichTrains;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.RunGates;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.autonomyeditor.StationSettingsWindow;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.StationDoors;

/**
 * Station Settings for several stations at once (FR-052), pressed as the user presses it on the frozen railway: Pick
 * Stations picks and drops them, a field they disagree on says "(mixed)" and is left alone, and a change reaches each
 * through the door one station's window takes, writing what the same change made one station at a time writes.
 *
 * @author Adam
 */
public class testSeveralStationsAtOnce
{
    private static support.FrozenEditor frozen;
    private static AutonomySession session;

    private static final String PAGE = "1 - Main";

    private LayoutEditor editor;
    private AutonomyEditorPanel panel;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        frozen = support.FrozenEditor.open();
        session = frozen.session;
    }

    @BeforeMethod
    public void openTheEditor() throws Exception
    {
        if (frozen == null) throw new SkipException("the class did not set up");

        frozen.putBack();

        open();
    }

    @AfterMethod(alwaysRun = true)
    public void closeTheEditor() throws Exception
    {
        support.FrozenEditor.dispose(editor);

        editor = null;

        support.AskingWindows.closeEveryDialog();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (frozen != null) frozen.close();
    }

    /**
     * Pick Stations picks a station with a click and drops it with another, marks the picks, and the window follows
     * them; putting the tool down drops them all and closes the window.
     *
     * MUTATION: have `pickStation` add a station clicked again instead of dropping it, and this fails.
     */
    @Test(timeOut = 120000)
    public void testPickStationsPicksAndDropsThem() throws Exception
    {
        List<TileKey> three = stations(3);

        StationSettingsWindow window = pick(three);

        for (TileKey each : three) assertEquals(mark(each), TileAnnotation.ToolMark.TOGETHER, each + " is not marked picked");

        assertEquals(window.squaresForTest(), three, "the window is not about the stations picked");

        click(three.get(1));

        assertNull(mark(three.get(1)), "a station clicked again is still marked picked");

        assertEquals(window.squaresForTest(), Arrays.asList(three.get(0), three.get(2)),
            "the window still sets a station clicked again");

        StationDoors.press(panel, "autonomy.pickStations");

        for (TileKey each : three) assertNull(mark(each), each + " is still marked once the tool is down");

        assertNull(StationDoors.showing(StationSettingsWindow.class), "the window stayed open once the picks were dropped");
    }

    /**
     * The picks are drawn in a color no other mark uses, nor the selection's outline (FR-159, OB-394): each of those
     * means something else on the same diagram.
     *
     * MUTATION: have `TileAnnotation.markColour` give TOGETHER `MARK_BLUE`, and this fails.
     */
    @Test(timeOut = 60000)
    public void testThePicksAreMarkedInAColorOfTheirOwn() throws Exception
    {
        java.awt.image.BufferedImage picked = drawn(bare(false).markedByATool(TileAnnotation.ToolMark.TOGETHER));

        int edge = picked.getRGB(30, 2) & 0xFFFFFF;

        assertNotEquals(edge, 0xFFFFFF, "the picked mark draws no edge where the others draw theirs");

        List<TileAnnotation> others = new ArrayList<>();

        for (TileAnnotation.ToolMark each : TileAnnotation.ToolMark.values())
        {
            if (each != TileAnnotation.ToolMark.TOGETHER) others.add(bare(false).markedByATool(each));
        }

        others.add(bare(true));

        for (TileAnnotation other : others)
        {
            assertFalse(colorsIn(drawn(other)).contains(edge), "the picked mark's edge, #" + Integer.toHexString(edge)
                + ", is drawn by " + other + " too");
        }
    }

    /**
     * Several stations' window has no matrix, picture or row about one station alone, and its name cannot be typed.
     *
     * MUTATION: have `showForHowMany` leave the side column showing, and this fails.
     */
    @Test(timeOut = 120000)
    public void testSeveralHaveNoMatrixPictureOrRowsOfOneStation() throws Exception
    {
        StationSettingsWindow window = pick(stations(3));

        Components.onTheEventThread(() -> { window.openAdvancedForTest(); return null; });

        for (String part : new String[] {"station.matrix", "station.picture", "station.arrivals", "station.exitGuard",
            "station.entryGuard", "station.home", "station.depart", "station.whichChoose", "station.unavailable"})
        {
            assertFalse(showing(window, part), part + " shows for several stations");
        }

        assertTrue(showing(window, "station.several"), "the window does not say several stations are set");

        assertFalse(Components.onTheEventThread(() -> StationDoors.part(window, "station.name", JTextField.class).isEditable()),
            "the name can be typed for several stations");

        for (String part : new String[] {"station.preset.TERMINUS", "station.stops", "station.maxLength", "station.priority",
            "station.whichBlack"})
        {
            assertTrue(showing(window, part) && StationDoors.live(window, part), part + " is not offered for several");
        }
    }

    /**
     * A field the stations disagree on says "(mixed)", and nothing is written to it unless the user changes it; a change
     * to another field leaves it as each station has it.
     *
     * MUTATION: have `agreed` answer the first station's value whatever the others hold, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAFieldTheyDisagreeOnSaysMixedAndIsLeftAlone() throws Exception
    {
        List<TileKey> three = stations(3);

        Components.onTheEventThread(() ->
        {
            session.setPointProperty(three.get(0), "maxTrainLength", 10);
            session.setPointProperty(three.get(1), "maxTrainLength", 20);
            session.setAutoDestination(three.get(1), !session.isAutoDestination(three.get(0)));

            for (TileKey each : three) session.setPointProperty(each, "priority", null);

            return null;
        });

        Map<TileKey, Object> lengths = read(three, "maxTrainLength");
        Map<TileKey, Boolean> chosen = new LinkedHashMap<>();

        for (TileKey each : three) chosen.put(each, Components.onTheEventThread(() -> session.isAutoDestination(each)));

        StationSettingsWindow window = pick(three);

        String mixed = I18n.t("autosetup.ui.stationMixed");

        assertEquals(StationDoors.text(window, "station.maxLength"), mixed, "lengths that differ do not say so");

        assertTrue(showing(window, "station.fullAutonomyMixed"), "May choose this station does not say it differs");

        assertFalse(StationDoors.on(window, "station.fullAutonomy"), "a box they differ on is ticked");

        assertEquals(StationDoors.text(window, "station.priority"), "0", "a priority they share is not shown");

        String before = setup();

        StationDoors.typeAndEnter(window, "station.maxLength", mixed);

        assertEquals(setup(), before, "Enter on \"(mixed)\" wrote to the stations");

        Components.onTheEventThread(() -> { window.openAdvancedForTest(); return null; });

        StationDoors.typeAndEnter(window, "station.priority", "7");

        StationDoors.close(window);

        assertEquals(read(three, "priority"), sameFor(three, 7), "the priority typed did not reach every station");

        assertEquals(read(three, "maxTrainLength"), lengths, "a change of priority wrote the lengths they differ on");

        for (TileKey each : three)
        {
            assertEquals(Components.onTheEventThread(() -> session.isAutoDestination(each)), chosen.get(each),
                "a change of priority wrote May choose this station, which they differ on");
        }
    }

    /**
     * A change in the window reaches every station picked: a preset, a box, a typed field and a radio.
     *
     * MUTATION: have `actOnEach` act on the first station only, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAChangeReachesEveryStationPicked() throws Exception
    {
        List<TileKey> three = stations(3);

        StationSettingsWindow window = pick(three);

        StationDoors.press(window, "station.preset.TERMINUS");

        for (TileKey each : three) assertTrue(on(() -> session.isMustTurnAround(each)), "Terminus missed " + each);

        StationDoors.typeAndEnter(window, "station.maxLength", "25");

        assertEquals(read(three, "maxTrainLength"), sameFor(three, 25), "the length typed missed a station");

        StationDoors.press(window, "station.turnMay");

        for (TileKey each : three)
        {
            assertTrue(on(() -> session.isTurnAround(each) && !session.isMustTurnAround(each)), "May turn missed " + each);
        }

        StationDoors.press(window, "station.runsThrough");

        for (TileKey each : three) assertTrue(on(() -> session.isStopOnly(each)), "Running through off missed " + each);
    }

    /**
     * The saved setup after a change made to several stations at once is the file the same change makes one station at
     * a time, byte for byte (Adam: "make sure behavior is unchanged").
     *
     * MUTATION: have `actOnEach` leave out the last station picked, and this fails.
     */
    @Test(timeOut = 300000)
    public void testTogetherSavesWhatOneAtATimeSaves() throws Exception
    {
        List<TileKey> three = stations(3);

        String opened = setup();

        Map<String, String> together = saved(three, true);

        assertNotEquals(together.get(SETUP), opened, "precondition: the change changed nothing");

        Map<String, String> oneByOne = saved(three, false);

        assertEquals(together.keySet(), oneByOne.keySet(), "the two saves wrote different files");

        for (String file : together.keySet())
        {
            assertEquals(together.get(file), oneByOne.get(file), file + " differs from the one written one station at a time");
        }
    }

    /**
     * A question one station's change asks is asked for that station among several, and its answer kept: a list that
     * shuts out a station's home locomotive (OB-022) asks, and No leaves that station alone while the others change.
     *
     * MUTATION: have `pressWhich` write through `session.setWhichTrains` instead of the panel's `setWhichTrains`, and
     * this fails.
     */
    @Test(timeOut = 180000)
    public void testAQuestionOneStationAsksIsAskedAmongSeveral() throws Exception
    {
        List<TileKey> two = stations(2);

        final TileKey homed = two.get(0);
        final TileKey other = two.get(1);

        final String train = aTrain();

        Components.onTheEventThread(() ->
        {
            session.setPointProperty(homed, "home", train);
            session.setWhichTrains(homed, WhichTrains.ALL, Collections.singletonList(train));
            session.setWhichTrains(other, WhichTrains.ALL, Collections.singletonList(train));

            return null;
        });

        Components.settle();

        StationSettingsWindow alone = StationDoors.open(panel, homed);

        List<String> askedAlone = StationDoors.press(alone, "station.whichBlack", message -> Answering.pressAt(1));

        assertEquals(askedAlone.size(), 1, "precondition: one station's window asks nothing about its home: " + askedAlone);

        StationDoors.close(alone);

        StationSettingsWindow window = pick(two);

        List<String> asked = StationDoors.press(window, "station.whichBlack", message -> Answering.pressAt(1));

        assertEquals(asked, askedAlone, "several stations did not ask what one station asks");

        assertEquals(on(() -> session.getWhichTrains(homed)), WhichTrains.ALL, "No did not keep the home's station as it was");

        assertEquals(on(() -> session.getWhichTrains(other)), WhichTrains.BLACKLIST, "the station with no home did not change");
    }

    /**
     * While a run holds the setup the window opens read-only for several stations, and a press that gets through anyway
     * is refused and writes nothing.
     *
     * MUTATION: drop the `refusedWhileRunning` check from `StationWindowBase.act`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testSeveralAreReadOnlyWhileARunHoldsTheSetup() throws Exception
    {
        final java.lang.reflect.Field staging = TrainControlUI.class.getDeclaredField("stagingFlowActive");

        staging.setAccessible(true);

        try
        {
            Components.onTheEventThread(() -> { staging.set(frozen.ui, true); return null; });

            StationSettingsWindow window = pick(stations(2));

            assertTrue(window.isReadOnly(), "several stations' window is not read-only during a run");

            assertFalse(StationDoors.live(window, "station.preset.TERMINUS"), "a preset is live during a run");

            String before = setup();

            List<String> said = StationDoors.pressThoughGrayed(StationDoors.part(window, "station.preset.TERMINUS",
                AbstractButton.class));

            assertTrue(said.contains(I18n.t(RunGates.NO_EDITS_WHILE_RUNNING)), "a press during a run did not say why: " + said);

            assertEquals(setup(), before, "a press during a run wrote to the stations");
        }
        finally
        {
            Components.onTheEventThread(() -> { staging.set(frozen.ui, false); return null; });
        }
    }

    // ------------------------------------------------------------------------------------------------------- helpers

    private static final String SETUP = "(the setup in memory)";

    /** The editor on the page. */
    private void open()
    {
        editor = frozen.editorOn(PAGE);

        panel = Components.onTheEventThread(() -> editor.getAutonomyPanel());
    }

    /** Arms Pick Stations and clicks each square; the window that opens. */
    private StationSettingsWindow pick(List<TileKey> squares) throws Exception
    {
        StationDoors.press(panel, "autonomy.pickStations");

        for (TileKey each : squares) click(each);

        StationSettingsWindow window = StationDoors.showing(StationSettingsWindow.class);

        assertNotNull(window, "picking stations opened no window");

        StationDoors.current(window);

        return window;
    }

    private void click(TileKey square)
    {
        Components.onTheEventThread(() ->
        {
            panel.tileClicked(square, session.getGraph().getTiles().get(square), false);

            return null;
        });

        Components.settle();
    }

    /**
     * The same change, to several stations at once or one at a time: Parking, Running through off, a maximum length,
     * a priority and a speed; then Save, and every file the sandbox holds, with the setup read back.
     */
    private Map<String, String> saved(List<TileKey> squares, boolean together) throws Exception
    {
        closeTheEditor();

        frozen.putBack();

        open();

        if (together) change(pick(squares));
        else for (TileKey each : squares) change(StationDoors.open(panel, each));

        AutonomySession reread = frozen.saveAndReadBack(editor);

        Map<String, String> out = new LinkedHashMap<>();

        out.put(SETUP, Components.onTheEventThread(() -> reread.snapshotSetup().toString()));

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(frozen.sandbox.getFolder().toPath()))
        {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) walk.filter(Files::isRegularFile).sorted()::iterator)
            {
                out.put(frozen.sandbox.getFolder().toPath().relativize(file).toString(),
                    new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        return out;
    }

    private static void change(StationSettingsWindow window) throws Exception
    {
        StationDoors.press(window, "station.preset.PARKING");
        StationDoors.press(window, "station.runsThrough");
        StationDoors.typeAndEnter(window, "station.maxLength", "33");

        if (!showing(window, "station.priority")) Components.onTheEventThread(() -> { window.openAdvancedForTest(); return null; });

        StationDoors.typeAndEnter(window, "station.priority", "4");
        StationDoors.typeAndEnter(window, "station.speed", "80");

        StationDoors.current(window);
    }

    /** The first named stations of the page that do not turn, in the order the setup has them. */
    private static List<TileKey> stations(int count)
    {
        return Components.onTheEventThread(() ->
        {
            List<TileKey> out = new ArrayList<>();

            for (TileKey each : session.getReducer().getPoints().keySet())
            {
                if (out.size() < count && PAGE.equals(each.getPage()) && session.getStore().isStation(each)
                    && !session.isTurnAround(each) && session.getStore().getPointName(each) != null) out.add(each);
            }

            assertEquals(out.size(), count, "precondition: too few plain stations on " + PAGE);

            return out;
        });
    }

    private static String aTrain()
    {
        return Components.onTheEventThread(() ->
        {
            Set<String> trains = new LinkedHashSet<>(session.trainsWhereTheyStand().values());

            assertFalse(trains.isEmpty(), "precondition: no train stands in the setup");

            return trains.iterator().next();
        });
    }

    private TileAnnotation.ToolMark mark(TileKey square)
    {
        return Components.onTheEventThread(() -> panel.annotationFor(square).getToolMark());
    }

    private static TileAnnotation bare(boolean selected)
    {
        return new TileAnnotation(Collections.<TileAnnotation.Mark>emptyList(), -1, selected);
    }

    private static java.awt.image.BufferedImage drawn(TileAnnotation annotation)
    {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(60, 60, java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = image.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, 60, 60);

            annotation.paint(g, 60, 60);
        }
        finally
        {
            g.dispose();
        }

        return image;
    }

    private static Set<Integer> colorsIn(java.awt.image.BufferedImage image)
    {
        Set<Integer> out = new LinkedHashSet<>();

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++) out.add(image.getRGB(x, y) & 0xFFFFFF);
        }

        return out;
    }

    private static Map<TileKey, Object> read(List<TileKey> squares, String key)
    {
        return Components.onTheEventThread(() ->
        {
            Map<TileKey, Object> out = new LinkedHashMap<>();

            for (TileKey each : squares)
            {
                Object stored = session.getPointProperty(each, key);

                out.put(each, stored instanceof Number ? ((Number) stored).intValue() : stored);
            }

            return out;
        });
    }

    private static Map<TileKey, Object> sameFor(List<TileKey> squares, Object value)
    {
        Map<TileKey, Object> out = new LinkedHashMap<>();

        for (TileKey each : squares) out.put(each, value);

        return out;
    }

    private static <T> T on(java.util.concurrent.Callable<T> question)
    {
        return Components.onTheEventThread(question);
    }

    private static String setup()
    {
        return Components.onTheEventThread(() -> session.snapshotSetup().toString());
    }

    private static boolean showing(java.awt.Component window, String name)
    {
        return Components.onTheEventThread(() ->
        {
            JComponent part = Components.named(window, name, JComponent.class);

            return part != null && part.isShowing();
        });
    }
}
