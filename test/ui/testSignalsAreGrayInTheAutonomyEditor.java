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
     * The same editor switched from Autonomy Setup back to Track Diagram draws its signals in color again (FR-193, Adam:
     * "Make sure it goes back to color in the track diagram editor too").
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
