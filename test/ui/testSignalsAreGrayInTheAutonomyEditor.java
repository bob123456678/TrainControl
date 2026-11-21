package ui;

import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.ImageIcon;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagramComponent;
import static org.traincontrol.base.LayoutDiagramComponent.componentType.LAMP;
import static org.traincontrol.base.LayoutDiagramComponent.componentType.SIGNAL;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinAccessory;
import support.Components;
import support.Pixels;
import support.Reflect;

/**
 * The autonomy editor draws signals and lamps in gray, and the signals that can be picked in color again while a guard
 * signal is being picked; everywhere else they keep their color and their aspect, whichever window opened first.
 */
public class testSignalsAreGrayInTheAutonomyEditor
{
    private static final String PAGE = "1 - Main";

    private static support.FrozenEditor frozen;

    @AfterClass(alwaysRun = true)
    public static void close() throws Exception
    {
        if (frozen != null) frozen.close();
    }

    @BeforeClass
    public static void open() throws Exception
    {
        frozen = support.FrozenEditor.open();
    }

    /**
     * Every signal square in the autonomy editor paints with no color but that of the editor's marks over it.
     *
     * MUTATION: drop the `inGray()` call in `AutonomyEditorPanel.annotationFor`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testASignalIsGrayInTheAutonomyEditor() throws Exception
    {
        allGrayInTheAutonomyEditor(SIGNAL, false);
    }

    /**
     * Every lamp square in the autonomy editor paints with no color, and stays so while a guard signal is being picked:
     * a lamp cannot be one.
     *
     * MUTATION: gray only `componentType.SIGNAL` in `AutonomyEditorPanel.annotationFor`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testALampIsGrayInTheAutonomyEditor() throws Exception
    {
        allGrayInTheAutonomyEditor(LAMP, true);
    }

    /** Every square of this type on the page gray in the autonomy editor, and while a pick is on if asked. */
    private static void allGrayInTheAutonomyEditor(LayoutDiagramComponent.componentType type, boolean whilePicking)
        throws Exception
    {
        LayoutEditor editor = frozen.editorOn(PAGE);
        AutonomyEditorPanel panel = Components.onTheEventThread(editor::getAutonomyPanel);

        try
        {
            List<LayoutLabel> squares = squaresIn(editor, type);

            assertFalse(squares.isEmpty(), "precondition: no " + type + " on " + PAGE);

            for (boolean picked : whilePicking ? new boolean[] {false, true} : new boolean[] {false})
            {
                if (picked) picking(panel, aStation());

                for (LayoutLabel square : squares)
                {
                    assertEquals(coloredArt(square), 0, "a " + type + " in the autonomy editor has color at "
                        + square.getComponent().getX() + "," + square.getComponent().getY()
                        + (picked ? " while a guard signal is picked" : ""));
                }
            }
        }
        finally
        {
            picking(panel, null);

            support.FrozenEditor.dispose(editor);
        }
    }

    /**
     * While a guard signal is being picked, a signal that can be picked is drawn in color, gray before and after.
     *
     * MUTATION: gray every signal in `annotationFor` whether or not it can be picked, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAPickableSignalIsInColorWhilePicking() throws Exception
    {
        LayoutEditor editor = frozen.editorOn(PAGE);
        AutonomyEditorPanel panel = Components.onTheEventThread(editor::getAutonomyPanel);

        try
        {
            LayoutLabel pickable = signalsIn(editor).stream()
                .filter(l -> l.getComponent().getAccessory() != null).findFirst().orElse(null);

            assertNotNull(pickable, "precondition: no signal with an address on " + PAGE);
            assertEquals(coloredArt(pickable), 0, "the signal is not gray before the pick");

            picking(panel, aStation());

            assertTrue(colored(pickable) > 20, "a signal that can be picked is not in color while picking");

            picking(panel, null);

            assertEquals(coloredArt(pickable), 0, "the signal is not gray again after the pick");
        }
        finally
        {
            picking(panel, null);

            support.FrozenEditor.dispose(editor);
        }
    }

    /**
     * A signal grayed in the autonomy editor is in color in the track diagram editor opened afterwards, and on the
     * track diagram with its aspect: the gray is never written into the tile images they share.
     *
     * MUTATION: gray the image in `LayoutLabel.setImageOnEDT`'s cache rather than as the tile paints, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheSignalKeepsItsColorElsewhere() throws Exception
    {
        keepsItsColorElsewhere(SIGNAL);
    }

    /**
     * The same for a lamp: grayed in the autonomy editor, in color in the track diagram editor opened afterwards, and
     * yellow on the track diagram while it is lit.
     *
     * MUTATION: gray the image in `LayoutLabel.setImageOnEDT`'s cache rather than as the tile paints, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheLampKeepsItsColorElsewhere() throws Exception
    {
        keepsItsColorElsewhere(LAMP);
    }

    private static void keepsItsColorElsewhere(LayoutDiagramComponent.componentType type) throws Exception
    {
        LayoutEditor autonomy = frozen.editorOn(PAGE);
        LayoutDiagramComponent square;

        try
        {
            LayoutLabel grayed = squaresIn(autonomy, type).get(0);

            square = grayed.getComponent();

            assertEquals(coloredArt(grayed), 0, "precondition: the " + type + " is not gray in the autonomy editor");
        }
        finally
        {
            support.FrozenEditor.dispose(autonomy);
        }

        LayoutEditor layoutEditor = Components.onTheEventThread(() ->
        {
            LayoutEditor built = new LayoutEditor(frozen.model.getLayout(PAGE), 30, frozen.ui, 0);

            built.render();

            return built;
        });

        try
        {
            Components.settle();

            // The editor draws a square at rest: a signal green, a lamp unlit, whose only color is its bulb's base.
            assertTrue(colored(same(squaresIn(layoutEditor, type), square)) > (type == SIGNAL ? 20 : 0),
                "the " + type + " is not in color in the track diagram editor");
        }
        finally
        {
            support.FrozenEditor.dispose(layoutEditor);
        }

        MarklinAccessory accessory = (MarklinAccessory) square.getAccessory();

        assertNotNull(accessory, "precondition: the " + type + " has no accessory");

        boolean was = accessory.isSwitched();

        // Lit, so the lamp has its yellow to show; the station is not asked.
        if (type == LAMP) accessory.adoptStationPosition(true, System.nanoTime());

        try
        {
            support.Rendered diagram = support.Rendered.open(frozen.model.getLayout(PAGE), frozen.ui);

            diagram.settle();

            LayoutLabel shown = same(Components.all(diagram.host(), LayoutLabel.class, l -> l.getComponent() != null),
                square);

            String aspect = type == LAMP ? "yellow" : accessory.isSwitched() ? "red" : "green";

            assertTrue(Pixels.count(Pixels.paint(shown, 30), rgb -> chroma(rgb) > 60 && hue(aspect, rgb)) > 20,
                "the " + type + " is not drawn " + aspect + " on the track diagram");
        }
        finally
        {
            accessory.adoptStationPosition(was, System.nanoTime());
        }
    }

    /**
     * The same editor switched from Autonomy Setup back to Track Diagram draws its signals in color again (FR-193).
     *
     * MUTATION: have `LayoutEditor.refreshAutonomyAnnotations` return when the panel is not active, so nothing clears the
     * gray, and this fails.
     */
    @Test(timeOut = 120000)
    public void testBackOnTrackDiagramTheSameEditorIsInColor() throws Exception
    {
        LayoutEditor editor = frozen.editorOn(PAGE);

        try
        {
            LayoutLabel grayed = signalsIn(editor).get(0);

            assertEquals(coloredArt(grayed), 0, "precondition: the signal is not gray in the autonomy editor");

            Components.onTheEventThread(() -> { editor.setAutonomyMode(null); return null; });
            Components.settle();

            assertTrue(green(same(signalsIn(editor), grayed.getComponent())) > 20, "the signal is still gray once the"
                + " editor is back on Track Diagram");
        }
        finally
        {
            support.FrozenEditor.dispose(editor);
        }
    }

    /**
     * Grayed-out Icons, unticked, leaves the squares autonomy ignores empty in the autonomy editor, the page's lamps
     * here, while its signals stay drawn in gray; the track diagram still draws the lamps; ticked again, the editor
     * draws them and the tick is remembered (FR-197; Adam: "option to hide greyed out icons").  A default start opens it
     * ticked, and a start with it remembered unticked opens unticked at the default start's size.
     *
     * MUTATION: drop the `leftEmpty()` call in `AutonomyEditorPanel.annotationFor`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testGrayedOutIconsUntickedLeavesTheIgnoredSquaresEmpty() throws Exception
    {
        java.util.prefs.Preferences prefs = org.traincontrol.util.Util.preferencesFor(org.traincontrol.gui.TrainControlUI.class);
        String was = prefs.get(PREF_IGNORED, null);

        LayoutEditor editor = null;

        try
        {
            prefs.remove(PREF_IGNORED);

            editor = frozen.editorOn(PAGE);

            javax.swing.JCheckBox box = grayedOutIcons(editor);

            assertTrue(Components.onTheEventThread(box::isSelected), "a default start opens with Grayed-out Icons ticked");

            java.awt.Dimension asDefault = Components.onTheEventThread(editor::getSize);

            for (LayoutLabel lamp : drawnSquares(editor, LAMP)) assertTrue(ink(lamp) > 0, "precondition: a lamp is not drawn");

            support.FrozenEditor.dispose(editor);

            prefs.putBoolean(PREF_IGNORED, false);

            editor = frozen.editorOn(PAGE);
            box = grayedOutIcons(editor);

            assertFalse(Components.onTheEventThread(box::isSelected), "a start with it remembered unticked opens it unticked");
            assertEquals(Components.onTheEventThread(editor::getSize), asDefault,
                "the editor started with Grayed-out Icons unticked is not the size of a default start");

            assertLeftEmpty(editor, drawnSquares(editor, LAMP), "unticked");

            for (LayoutLabel signal : drawnSquares(editor, SIGNAL))
            {
                assertTrue(ink(signal) > 0, "unticked, a signal is left empty");
                assertEquals(coloredArt(signal), 0, "unticked, a signal is not gray");
            }

            support.Rendered diagram = support.Rendered.open(frozen.model.getLayout(PAGE), frozen.ui);

            diagram.settle();

            for (LayoutLabel lamp : drawnSquares(diagram.host(), LAMP))
            {
                assertTrue(ink(lamp) > 0, "with the editor's box unticked, the track diagram leaves a lamp empty");
            }

            final javax.swing.JCheckBox ticked = box;

            Components.onTheEventThread(() -> { ticked.doClick(); return null; });
            Components.settle();

            for (LayoutLabel lamp : drawnSquares(editor, LAMP)) assertTrue(ink(lamp) > 0, "ticked again, a lamp is not drawn");

            assertTrue(prefs.getBoolean(PREF_IGNORED, false), "the tick was not remembered");
        }
        finally
        {
            support.FrozenEditor.dispose(editor);

            if (was == null) prefs.remove(PREF_IGNORED);
            else prefs.put(PREF_IGNORED, was);
        }
    }

    /**
     * Unticking Grayed-out Icons on an open editor empties its lamps at once, and ticking it again draws them gray
     * (FR-197): the box acts on the editor as it stands, not only at the next opening.
     *
     * MUTATION: drop `empty == other.empty` from `TileAnnotation.equals`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testUntickingGrayedOutIconsEmptiesAnOpenEditor() throws Exception
    {
        withGrayedOutIcons(null, PAGE, editor ->
        {
            javax.swing.JCheckBox box = grayedOutIcons(editor);
            List<LayoutLabel> lamps = drawnSquares(editor, LAMP);

            for (LayoutLabel lamp : lamps) assertTrue(ink(lamp) > 0, "precondition: a lamp is not drawn");

            Components.onTheEventThread(() -> { box.doClick(); return null; });
            Components.settle();

            assertLeftEmpty(editor, lamps, "unticked on an open editor");

            Components.onTheEventThread(() -> { box.doClick(); return null; });
            Components.settle();

            for (LayoutLabel lamp : lamps)
            {
                assertTrue(ink(lamp) > 0, "ticked again, a lamp is left empty");
                assertEquals(coloredArt(lamp), 0, "ticked again, a lamp is not gray");
            }
        });
    }

    /**
     * With Grayed-out Icons unticked, a flash on a square left empty shows: a finding on an ignored square, clicked,
     * scrolls to it and flashes it, so it must not stay blank (FR-197).
     *
     * MUTATION: drop `flashTimer == null` from the left-empty branch of `LayoutLabel.paintComponent`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testAFlashShowsOnASquareLeftEmpty() throws Exception
    {
        withGrayedOutIcons(false, PAGE, editor ->
        {
            LayoutLabel lamp = drawnSquares(editor, LAMP).get(0);

            assertLeftEmpty(editor, java.util.Collections.singletonList(lamp), "precondition: unticked");

            // A flash held for 3 seconds, so the picture is taken while it is up
            Components.onTheEventThread(() -> { lamp.flashHighlight(java.awt.Color.YELLOW, 3000); return null; });

            assertTrue(Pixels.differing(Pixels.paint(lamp, 30), emptySquare(editor)) > 0,
                "a flash on a lamp left empty shows nothing");

            assertTrue(Components.waitFor(() -> !Components.onTheEventThread(lamp::isFlashOutstanding), 20000),
                "the flash never ended");
        });
    }

    /**
     * With Grayed-out Icons unticked, a switched-off link stays drawn: it is track the user turned off, not a square
     * autonomy cannot use (FR-197).
     *
     * MUTATION: leave `isIgnored(tile)` out of the left-empty test in `AutonomyEditorPanel.annotationFor`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testASwitchedOffLinkStaysDrawnUnticked() throws Exception
    {
        TileKey link = Components.onTheEventThread(() -> frozen.session.getGraph().getTiles().entrySet().stream()
            .filter(e -> org.traincontrol.automationui.TilePorts.hasPortal(e.getValue().getType())).map(e -> e.getKey())
            .findFirst().orElse(null));

        assertNotNull(link, "precondition: the railway has no link");

        try
        {
            Components.onTheEventThread(() -> { frozen.session.setPortalDisabled(link, true); return null; });

            withGrayedOutIcons(false, link.getPage(), editor ->
            {
                LayoutLabel shown = Components.all(editor, LayoutLabel.class, l -> l.getComponent() != null
                    && l.getComponent().getX() == link.getX() && l.getComponent().getY() == link.getY()).get(0);

                same(java.util.Collections.singletonList(shown), shown.getComponent());

                assertTrue(Pixels.differing(Pixels.paint(shown, 30), emptySquare(editor)) > 0, "unticked, the switched-off"
                    + " link at " + link + " is left empty");
            });
        }
        finally
        {
            frozen.putBack();
        }
    }

    /** What a claim does on the editor it is given. */
    private interface OnTheEditor
    {
        void check(LayoutEditor editor) throws Exception;
    }

    /** A claim on a page's editor opened with Grayed-out Icons remembered so (null: never set); the preference put back. */
    private static void withGrayedOutIcons(Boolean remembered, String page, OnTheEditor claim) throws Exception
    {
        java.util.prefs.Preferences prefs = org.traincontrol.util.Util.preferencesFor(org.traincontrol.gui.TrainControlUI.class);
        String was = prefs.get(PREF_IGNORED, null);

        LayoutEditor editor = null;

        try
        {
            if (remembered == null) prefs.remove(PREF_IGNORED);
            else prefs.putBoolean(PREF_IGNORED, remembered);

            editor = frozen.editorOn(page);

            claim.check(editor);
        }
        finally
        {
            support.FrozenEditor.dispose(editor);

            if (was == null) prefs.remove(PREF_IGNORED);
            else prefs.put(PREF_IGNORED, was);
        }
    }

    /** Each square drawn as a square with nothing on it, which keeps the editor's grid lines. */
    private static void assertLeftEmpty(LayoutEditor editor, List<LayoutLabel> squares, String what) throws Exception
    {
        BufferedImage empty = emptySquare(editor);

        for (LayoutLabel square : squares)
        {
            assertEquals(Pixels.differing(Pixels.paint(square, 30), empty), 0, what + ", a square is drawn at "
                + square.getComponent().getX() + "," + square.getComponent().getY());
        }
    }

    private static BufferedImage emptySquare(LayoutEditor editor) throws Exception
    {
        return Pixels.paint(Components.all(editor, LayoutLabel.class, l -> l.getComponent() == null).get(0), 30);
    }

    private static final String PREF_IGNORED = "autonomyEditorIgnoredSquares";

    /** The autonomy editor's Grayed-out Icons box, by its words. */
    private static javax.swing.JCheckBox grayedOutIcons(LayoutEditor editor)
    {
        javax.swing.JCheckBox box = Components.onTheEventThread(() -> editor.getAutonomyPanel().getShowIgnored());

        assertEquals(Components.onTheEventThread(box::getText), "Grayed-out Icons",
            "the box is not Grayed-out Icons");
        assertTrue(Components.onTheEventThread(box::isShowing), "the Grayed-out Icons box is not in the editor");

        return box;
    }

    /** Every square of this type on the page, each once its picture has arrived, so an empty one is left empty. */
    private static List<LayoutLabel> drawnSquares(java.awt.Container window, LayoutDiagramComponent.componentType type)
    {
        List<LayoutLabel> squares = squaresIn(window, type);

        assertFalse(squares.isEmpty(), "precondition: no " + type + " on " + PAGE);

        for (LayoutLabel square : squares) same(squares, square.getComponent());

        return squares;
    }

    /** Pixels in any but the square's commonest color. */
    private static int ink(LayoutLabel tile) throws Exception
    {
        return Pixels.drawn(Pixels.paint(tile, 30));
    }

    /** Sets or clears the station a guard signal is being picked for, as "click it on the diagram" does. */
    private static void picking(AutonomyEditorPanel panel, TileKey station) throws Exception
    {
        Components.onTheEventThread(() ->
        {
            Reflect.set(AutonomyEditorPanel.class, panel, "signalFor", station);
            panel.refresh();

            return null;
        });

        Components.settle();
    }

    private static TileKey aStation()
    {
        return Components.onTheEventThread(() -> frozen.session.getReducer().getPoints().keySet()
            .stream().filter(k -> frozen.session.getStore().isStation(k)).findFirst().get());
    }

    private static List<LayoutLabel> signalsIn(java.awt.Container window)
    {
        return squaresIn(window, SIGNAL);
    }

    private static List<LayoutLabel> squaresIn(java.awt.Container window, LayoutDiagramComponent.componentType type)
    {
        return Components.all(window, LayoutLabel.class, l -> l.getComponent() != null && l.getComponent().getType() == type);
    }

    /** The label showing the same square as `signal`, its picture applied. */
    private static LayoutLabel same(List<LayoutLabel> labels, LayoutDiagramComponent signal)
    {
        LayoutLabel found = labels.stream().filter(l -> l.getComponent().getX() == signal.getX()
            && l.getComponent().getY() == signal.getY()).findFirst().orElse(null);

        assertNotNull(found, "precondition: the signal at " + signal.getX() + "," + signal.getY() + " is not shown");
        assertTrue(Components.waitFor(() -> found.getIcon() instanceof ImageIcon, 10000),
            "precondition: the signal's picture never arrived");

        return found;
    }

    /** Pixels with any color at all, beyond rounding. */
    private static int colored(LayoutLabel tile) throws Exception
    {
        return Pixels.count(Pixels.paint(tile, 30), rgb -> chroma(rgb) > 12);
    }

    /** Colored pixels that the editor's own marks over the square (its arrows, say) do not account for. */
    private static int coloredArt(LayoutLabel tile) throws Exception
    {
        BufferedImage painted = Pixels.paint(tile, 30);
        BufferedImage marks = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);

        Object annotation = Reflect.get(LayoutLabel.class, tile, "autonomyAnnotation");

        java.awt.Graphics2D g = marks.createGraphics();

        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 30, 30);

        if (annotation != null) ((org.traincontrol.automationui.TileAnnotation) annotation).paint(g, 30, 30);

        g.dispose();

        int found = 0;

        for (int x = 0; x < 30; x++)
        {
            for (int y = 0; y < 30; y++)
            {
                if (chroma(painted.getRGB(x, y)) > 12 && chroma(marks.getRGB(x, y)) == 0) found++;
            }
        }

        return found;
    }

    private static int green(LayoutLabel tile) throws Exception
    {
        return Pixels.count(Pixels.paint(tile, 30), rgb -> chroma(rgb) > 60 && hue("green", rgb));
    }

    /** Whether a strongly colored pixel is this one of a signal's or a lamp's colors. */
    private static boolean hue(String aspect, int rgb)
    {
        int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;

        return aspect.equals("yellow") ? r > 150 && g > 150 && b < 100 : aspect.equals("red") ? r > g : g > r;
    }

    private static int chroma(int rgb)
    {
        int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;

        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }
}
