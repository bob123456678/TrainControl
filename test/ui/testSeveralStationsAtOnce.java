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
import support.Reflect;
import support.StationDoors;

/**
 * Station Settings for several stations at once (FR-052), pressed as the user presses it on the frozen railway: Pick
 * Stations picks and drops them, a field they disagree on wears a warning triangle and is left alone, and a change reaches each
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

        assertEquals(mark(three.get(1)), TileAnnotation.ToolMark.PICKABLE, "a station clicked again is still marked picked");

        assertEquals(window.squaresForTest(), Arrays.asList(three.get(0), three.get(2)),
            "the window still sets a station clicked again");

        StationDoors.press(panel, "autonomy.pickStations");

        for (TileKey each : three) assertNull(mark(each), each + " is still marked once the tool is down");

        assertNull(StationDoors.showing(StationSettingsWindow.class), "the window stayed open once the picks were dropped");
    }

    /**
     * Several stations show a green banner at the top naming every one in full, in the editor's green for a ready setup
     * with black text, not the yellow of a warning; they leave out the Name row; with one station left the banner goes
     * and the Name row is back.
     *
     * MUTATION: stop calling `sayWhichArePicked` in `fill`, or leave the Name row out of `showForHowMany`, or give the
     * banner the strip's yellow again, and this fails.
     */
    @Test(timeOut = 120000)
    public void testSeveralStationsAreNamedInAGreenBannerAndHaveNoNameRow() throws Exception
    {
        List<TileKey> three = stations(3);

        StationSettingsWindow window = pick(three);

        javax.swing.JLabel banner = StationDoors.part(window, "station.picked", javax.swing.JLabel.class);

        assertTrue(on(banner::isShowing), "no banner names the stations picked");
        assertEquals(on(banner::getBackground), new java.awt.Color(214, 245, 214), "the banner is not the editor's green");
        assertEquals(on(banner::getForeground), java.awt.Color.BLACK, "the banner's text is not black");
        assertNotEquals(on(banner::getBackground), StationDoors.part(window, "station.strip", javax.swing.JLabel.class)
            .getBackground(), "the banner is the yellow of the window's warnings");

        for (TileKey each : three)
        {
            String named = on(() -> session.getStore().getPointName(each));

            assertTrue(on(banner::getText).contains(named), "the banner leaves out " + named + ": " + on(banner::getText));
        }

        assertFalse(showing(window, "station.name"), "the Name box shows for several stations");
        assertFalse(on(() -> Components.find(window, javax.swing.JLabel.class,
            l -> I18n.t("autosetup.ui.stationName").equals(l.getText())).isShowing()), "the Name label shows for several");

        click(three.get(1));
        click(three.get(2));

        StationDoors.current(window);

        assertFalse(on(banner::isShowing), "the banner stays for one station");
        assertTrue(showing(window, "station.name"), "the Name box is gone for one station");
    }

    /**
     * The tool is called Bulk Edit...; while it is on every square a click can pick wears the editor's light gray grid
     * line, a pick keeps its own mark over it, squares that cannot be picked wear nothing, and nothing is outlined once
     * the tool is down.
     *
     * MUTATION: have `toolMark` answer null for a station that is not picked, or PICKABLE for a square that cannot be
     * picked, or leave the mark on when the tool goes down, or color PICKABLE otherwise than `Color.LIGHT_GRAY`, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testBulkEditOutlinesWhatCanBePickedInLightGray() throws Exception
    {
        assertEquals(StationDoors.part(panel, "autonomy.pickStations", AbstractButton.class).getText(), "Bulk Edit...");

        List<TileKey> tiles = Components.onTheEventThread(() ->
        {
            List<TileKey> out = new ArrayList<>();

            for (TileKey each : session.getGraph().getTiles().keySet())
            {
                if (PAGE.equals(each.getPage())) out.add(each);
            }

            return out;
        });

        for (TileKey each : tiles) assertNull(mark(each), "precondition: " + each + " is marked with the tool down");

        StationDoors.press(panel, "autonomy.pickStations");

        TileKey picked = stations(1).get(0);

        click(picked);

        int outlined = 0;
        int bare = 0;

        for (TileKey each : tiles)
        {
            boolean can = on(() -> panel.offersStationSettings(each));

            TileAnnotation.ToolMark expected = each.equals(picked) ? TileAnnotation.ToolMark.TOGETHER
                : can ? TileAnnotation.ToolMark.PICKABLE : null;

            assertEquals(mark(each), expected, each + " is marked wrongly while Bulk Edit is on");

            if (expected == TileAnnotation.ToolMark.PICKABLE) outlined++;
            else if (expected == null) bare++;
        }

        assertTrue(outlined > 5 && bare > 5, "precondition: " + outlined + " outlined and " + bare + " bare squares");

        java.awt.image.BufferedImage square = drawn(bare(false).markedByATool(TileAnnotation.ToolMark.PICKABLE));

        assertEquals(square.getRGB(0, 30) & 0xFFFFFF, java.awt.Color.LIGHT_GRAY.getRGB() & 0xFFFFFF, "the outline is not light gray");
        assertEquals(square.getRGB(30, 30) & 0xFFFFFF, 0xFFFFFF, "the outline washes the square");

        StationDoors.press(panel, "autonomy.pickStations");

        for (TileKey each : tiles) assertNull(mark(each), each + " is still marked after Bulk Edit ended");
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
     * A blank Enter leaves a length or priority they differ on as each has it, where before it cleared them all, so the
     * triangle and the row's (i) say how to set them all instead: 0 for any length, 0 for the default priority.
     *
     * MUTATION: give the two fields' triangles and flyovers the plain `mixedTip()`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testABlankLeavesThemAloneAndTheTipSaysHowToClearThem() throws Exception
    {
        StationSettingsWindow window = pickDiffering("maxTrainLength", "priority");

        Components.onTheEventThread(() -> { window.openAdvancedForTest(); return null; });

        StationDoors.current(window);

        String[][] fields = {{"maxLength", "autosetup.ui.stationMixedLengthTip"},
            {"priority", "autosetup.ui.stationMixedPriorityTip"}};

        for (String[] field : fields)
        {
            String how = I18n.t(field[1]);

            javax.swing.JLabel triangle = StationDoors.part(window, "station." + field[0] + "Mixed", javax.swing.JLabel.class);

            assertTrue(on(triangle::isShowing), "precondition: no triangle beside " + field[0]);
            assertTrue(on(triangle::getToolTipText).contains(how), field[0] + "'s triangle does not say how to set them all: "
                + on(triangle::getToolTipText));
            assertTrue(on(() -> window.flyoverForTest(field[0])).contains(how), field[0] + "'s (i) does not say how to set"
                + " them all");
        }

        String before = setup();

        StationDoors.typeAndEnter(window, "station.priority", "");

        assertEquals(setup(), before, "a blank Enter on the priority they differ on wrote to the stations");
    }

    /**
     * A triangle grays with its row: with Trains can stop here unticked for all, the triangle beside Maximum train length
     * is disabled and drawn gray, as the row's field is.  (Unticked, May choose this station reads the same for both.)
     *
     * MUTATION: leave the triangles out of their section's list, or drop `mixedLabel`'s gray disabled icon, and this fails.
     */
    @Test(timeOut = 120000)
    public void testATriangleGraysWithItsRow() throws Exception
    {
        StationSettingsWindow window = pickDiffering("maxTrainLength");

        StationDoors.press(window, "station.stops", message -> Answering.pressAt(0));
        StationDoors.current(window);

        assertFalse(StationDoors.on(window, "station.stops"), "precondition: Trains can stop here is still ticked");
        assertFalse(StationDoors.live(window, "station.maxLength"), "precondition: the length field did not gray");

        javax.swing.JLabel triangle = StationDoors.part(window, "station.maxLengthMixed", javax.swing.JLabel.class);

        assertTrue(on(triangle::isShowing), "precondition: the length's triangle is not showing");
        assertFalse(on(triangle::isEnabled), "the triangle stays live in a grayed row");
        assertNotNull(on(triangle::getDisabledIcon), "the triangle has no grayed drawing, so Swing draws nothing");

        List<Integer> pixels = on(() -> painted(triangle.getDisabledIcon()));

        assertTrue(pixels.stream().anyMatch(p -> (p >>> 24) > 0), "the triangle's grayed drawing is empty");

        for (int p : pixels)
        {
            if ((p >>> 24) == 0) continue;

            assertTrue(((p >> 16) & 0xff) == ((p >> 8) & 0xff) && ((p >> 8) & 0xff) == (p & 0xff),
                "the triangle is drawn in color in a grayed row: #" + Integer.toHexString(p));
        }
    }

    /**
     * A field the stations disagree on is blank beside a warning triangle, and nothing is written to it unless the user
     * changes it; a change to another field leaves it as each station has it.
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

        assertEquals(StationDoors.text(window, "station.maxLength"), "", "lengths that differ show a value");
        assertTrue(showing(window, "station.maxLengthMixed"), "lengths that differ do not say so");

        assertTrue(showing(window, "station.fullAutonomyMixed"), "May choose this station does not say it differs");

        assertFalse(StationDoors.on(window, "station.fullAutonomy"), "a box they differ on is ticked");

        String before = setup();

        StationDoors.typeAndEnter(window, "station.maxLength", "");

        assertEquals(setup(), before, "Enter on the blank they differ on wrote to the stations");

        Components.onTheEventThread(() -> { window.openAdvancedForTest(); return null; });

        assertEquals(StationDoors.text(window, "station.priority"), "0", "a priority they share is not shown");
        assertFalse(showing(window, "station.priorityMixed"), "a priority they share is marked as differing");

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
     * A question one station's change asks is asked for that station among several, and its No stops there: a list that
     * shuts out a station's home locomotive (OB-022) asks, No keeps that station and those after it as they were, and
     * the window says how many changed.
     *
     * MUTATION: have `actOnEach` go on past a door that answers false, and this fails.
     */
    @Test(timeOut = 180000)
    public void testAQuestionOneStationAsksIsAskedAmongSeveral() throws Exception
    {
        List<TileKey> three = stations(3);

        final TileKey other = three.get(0);
        final TileKey homed = three.get(1);
        final TileKey after = three.get(2);

        final String train = aTrain();

        Components.onTheEventThread(() ->
        {
            session.setPointProperty(homed, "home", train);

            for (TileKey each : three) session.setWhichTrains(each, WhichTrains.ALL, Collections.singletonList(train));

            return null;
        });

        Components.settle();

        StationSettingsWindow alone = StationDoors.open(panel, homed);

        List<String> askedAlone = StationDoors.press(alone, "station.whichBlack", message -> Answering.pressAt(1));

        assertEquals(askedAlone.size(), 1, "precondition: one station's window asks nothing about its home: " + askedAlone);

        StationDoors.close(alone);

        StationSettingsWindow window = pick(three);

        List<String> asked = StationDoors.press(window, "station.whichBlack", message -> Answering.pressAt(1));

        assertEquals(asked, askedAlone, "several stations did not ask what one station asks");

        assertEquals(on(() -> session.getWhichTrains(other)), WhichTrains.BLACKLIST, "the station before the No did not change");

        assertEquals(on(() -> session.getWhichTrains(homed)), WhichTrains.ALL, "No did not keep the home's station as it was");

        assertEquals(on(() -> session.getWhichTrains(after)), WhichTrains.ALL, "the station after the No changed all the same");

        assertEquals(StationDoors.said(window, "station.strip"), I18n.f("autosetup.ui.stationPartlyChanged", 1, 3),
            "the window does not say that only some changed");
    }

    /**
     * Nameless sensors picked together and made stations ask each its name, naming the square, as one at a time does;
     * Cancel on one stops there, those before keep their change, the rest stay as they were, and the window says how
     * many changed.
     *
     * MUTATION: have `askTheNewStationsName` ask with the one-station prompt whatever `oneOfSeveral` says, and this fails.
     */
    @Test(timeOut = 180000)
    public void testACanceledNameStopsTheRestAndSaysSo() throws Exception
    {
        final List<TileKey> three = on(() ->
        {
            List<TileKey> out = new ArrayList<>();

            for (TileKey each : session.getReducer().getPoints().keySet())
            {
                String name = session.getStore().getPointName(each);

                if (out.size() < 3 && PAGE.equals(each.getPage()) && !session.getStore().isStation(each)
                    && (name == null || name.trim().isEmpty())) out.add(each);
            }

            assertEquals(out.size(), 3, "precondition: too few nameless sensors on " + PAGE);

            return out;
        });

        final List<String> called = on(() ->
        {
            List<String> out = new ArrayList<>();

            for (TileKey each : three) out.add(session.describeTile(each));

            return out;
        });

        StationSettingsWindow window = pick(three);

        final int[] asking = {0};

        List<String> asked = StationDoors.press(window, "station.stops", message ->
            ++asking[0] == 1 ? Answering.type("Zed Alpha") : Answering.pressAt(1));

        assertEquals(asked.size(), 2, "the questions did not stop at the Cancel: " + asked);

        for (int i = 0; i < 2; i++)
        {
            assertTrue(asked.get(i).contains(called.get(i)), "question " + (i + 1) + " does not name " + called.get(i)
                + ", so the same words are asked of each: " + asked.get(i));
        }

        assertEquals(on(() -> session.getStore().getPointName(three.get(0))), "Zed Alpha", "the first did not take its name");
        assertTrue(on(() -> session.getStore().isStation(three.get(0))), "the first did not become a station");

        for (TileKey each : three.subList(1, 3))
        {
            assertFalse(on(() -> session.getStore().isStation(each)), each + " changed after the Cancel");
        }

        assertEquals(StationDoors.said(window, "station.strip"), I18n.f("autosetup.ui.stationPartlyChanged", 1, 3),
            "the window does not say that only one of the three changed");
    }

    /**
     * Where they differ, a warning triangle stands in for "(mixed)", and hovering it says they differ and each keeps its
     * own; the line saying changes apply to every station picked points at the triangle and is item text: black, in the
     * standard's regular text (Adam: "blue for section headings, black for item text").
     *
     * MUTATION: give `mixedLabel` its "(mixed)" text back instead of `RowIcons.warning`, or drop its tooltip, or the
     * line its black, and this fails.
     */
    @Test(timeOut = 120000)
    public void testATriangleMarksWhatTheyDifferOnAndTheLineAboveSaysSo() throws Exception
    {
        final List<TileKey> two = stations(2);

        Components.onTheEventThread(() ->
        {
            session.setAutoDestination(two.get(0), true);
            session.setAutoDestination(two.get(1), false);

            return null;
        });

        StationSettingsWindow window = pick(two);

        javax.swing.JLabel triangle = StationDoors.part(window, "station.fullAutonomyMixed", javax.swing.JLabel.class);

        assertTrue(on(triangle::isShowing), "nothing marks May choose this station, which they differ on");
        assertTrue(on(() -> triangle.getText() == null || triangle.getText().isEmpty()), "the mark still says "
            + on(triangle::getText));
        assertNotNull(on(triangle::getIcon), "the mark has no triangle");
        assertEquals(on(() -> painted(triangle.getIcon())), on(() -> painted(org.traincontrol.gui.widgets.RowIcons.warning(16))),
            "the mark is not the warning triangle");
        assertEquals(on(triangle::getToolTipText), AutonomyEditorPanel.wrapped(I18n.t("autosetup.ui.stationMixedTip")),
            "hovering the triangle does not say they differ and each keeps its own");

        javax.swing.JLabel line = StationDoors.part(window, "station.several", javax.swing.JLabel.class);

        assertTrue(on(line::isShowing), "precondition: the line above is not showing");
        assertTrue(on(line::getText).contains("warning triangle") && !on(line::getText).contains("(mixed)"),
            "the line above does not point at the triangle: " + on(line::getText));
        assertEquals(on(line::getForeground), java.awt.Color.BLACK, "the line above is not black");
        assertEquals(on(line::getFont), new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14),
            "the line above is not the standard's regular text");
    }

    /** An icon's pixels, to tell one drawing from another. */
    private static List<Integer> painted(javax.swing.Icon icon)
    {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        icon.paintIcon(null, g, 0, 0);
        g.dispose();

        List<Integer> out = new ArrayList<>();

        for (int each : image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth())) out.add(each);

        return out;
    }

    /**
     * A picked station that goes while the window is open (its page left out) is dropped from the picks, and the
     * window goes on for the rest, unmarked where it went.
     *
     * MUTATION: have `StationSettingsWindow.stillAbout` answer false for any square gone, as it did, and the window
     * closes and this fails.
     */
    @Test(timeOut = 180000)
    public void testAPickedStationThatGoesIsDropped() throws Exception
    {
        final TileKey far = on(() ->
        {
            for (TileKey each : session.getReducer().getPoints().keySet())
            {
                if (!PAGE.equals(each.getPage()) && session.getStore().isStation(each)) return each;
            }

            return null;
        });

        assertNotNull(far, "precondition: no station on another page than " + PAGE);

        List<TileKey> two = stations(2);

        closeTheEditor();

        editor = frozen.editorOn(far.getPage());

        panel = Components.onTheEventThread(() -> editor.getAutonomyPanel());

        List<TileKey> picks = Arrays.asList(far, two.get(0), two.get(1));

        StationSettingsWindow window = pick(picks);

        assertEquals(window.squaresForTest(), picks, "precondition: the window is not about the three picked");

        AbstractButton exclude = on(() -> panel.getExcludePage());

        assertNotNull(exclude, "precondition: the editor has no Exclude Page");

        StationDoors.pressAndAnswer(exclude, message -> Answering.pressAt(0));

        StationDoors.current(window);

        assertTrue(on(window::isShowing), "the window closed when one of the stations picked went");

        assertEquals(window.squaresForTest(), two, "the window is not about the stations left");

        assertNull(mark(far), "the station that went is still marked picked");
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
        final java.lang.reflect.Field staging = Reflect.field(TrainControlUI.class, "stagingFlowActive");

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

    /** Two plain stations picked together, differing on each of these numbers (1 and 2). */
    private StationSettingsWindow pickDiffering(String... properties) throws Exception
    {
        List<TileKey> two = stations(2);

        Components.onTheEventThread(() ->
        {
            for (String each : properties)
            {
                session.setPointProperty(two.get(0), each, 1);
                session.setPointProperty(two.get(1), each, 2);
            }

            return null;
        });

        return pick(two);
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
