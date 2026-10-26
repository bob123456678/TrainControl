package regression;

import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * Picking a tile out of "new components" and clicking a square puts that tile on the square.
 *
 * Adam, OB-169: "clicking a tile in 'new components' followed by a square on the diagram no longer places that tile.
 * it should place it and stay in place mode until escape is pressed or another action taken."
 *
 * **The square he was clicking already had track on it.**  Pressing the mouse on a square that carries a component
 * picks the tile up to be dragged, so the press handler armed a MOVE and threw away the palette selection; the release
 * then saw press and release on the same square, called it a click, and cleared the clipboard, leaving no tool for the
 * click event.  On an EMPTY square a press picks nothing up, which is why this went unnoticed and why the test places
 * over occupied track.
 *
 * The gesture is driven through the editor's own three handlers in the order a mouse produces them - beginDrag,
 * endDrag, receiveClickEvent - rather than through executeTool, which was never the broken part.
 */
public class testThePaletteStillPlacesTiles
{
    /**
     * A tile from the palette lands on a square that already has track on it.
     *
     * MUTATION: putting back the press handler's unconditional `initCopy(label, null, true)` - the pick-up-to-drag arm
     * - fails this.
     */
    @Test
    public void testAPaletteTileLandsOnOccupiedTrack() throws Exception
    {
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];
        final org.traincontrol.gui.layouteditor.LayoutEditor[] editor = new org.traincontrol.gui.layouteditor.LayoutEditor[1];

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;

        try
        {
            // Before the model, not just before the window (OB-111): constructing a TrainControlUI reads the
            // layout-path preference.
            sandbox = support.LayoutSandbox.open();

            model = support.TestStation.simulatedInDebug();

            final org.traincontrol.marklin.MarklinControlStation finalModel = model;

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    ui[0] = new org.traincontrol.gui.TrainControlUI();
                    ui[0].setViewListener(finalModel, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            java.io.File autonomyFolder =
                java.nio.file.Files.createTempDirectory("tc-palette").toFile();

            AutonomySession session = new AutonomySession(autonomyFolder);

            LayoutDiagram diagram = new LayoutDiagram("Palette Page", 12, 8, null, null);

            // THE SQUARE IS OCCUPIED, the case that fails: a straight, so the palette tile is a different type and the
            // assertion cannot pass by accident.
            diagram.addComponent(componentType.STRAIGHT, 4, 2, 0, 0, 0, 0,
                accessoryDecoderType.MM2, null);

            diagram.setEdit(true);
            diagram.checkBounds();

            session.open(Arrays.asList(diagram));

            java.lang.reflect.Field sessionField =
                org.traincontrol.gui.TrainControlUI.class.getDeclaredField("autonomySession");
            sessionField.setAccessible(true);
            sessionField.set(ui[0], session);

            javax.swing.SwingUtilities.invokeAndWait(() ->
                editor[0] = new org.traincontrol.gui.layouteditor.LayoutEditor(diagram, 30, ui[0], 0));

            java.lang.reflect.Method drawGrid =
                org.traincontrol.gui.layouteditor.LayoutEditor.class.getDeclaredMethod("drawGrid");
            drawGrid.setAccessible(true);

            java.lang.reflect.Field gridField =
                org.traincontrol.gui.layouteditor.LayoutEditor.class.getDeclaredField("grid");
            gridField.setAccessible(true);

            java.lang.reflect.Field paletteField =
                org.traincontrol.gui.layouteditor.LayoutEditor.class.getDeclaredField("newComponents");
            paletteField.setAccessible(true);

            final String[] failure = new String[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    drawGrid.invoke(editor[0]);

                    org.traincontrol.gui.LayoutGrid grid =
                        (org.traincontrol.gui.LayoutGrid) gridField.get(editor[0]);

                    org.traincontrol.gui.LayoutLabel square = grid.getValueAt(4, 2);

                    if (square == null || square.getComponent() == null)
                    {
                        failure[0] = "the fixture square has no track on it, so this would be the "
                            + "empty-square case that already works";
                        return;
                    }

                    org.traincontrol.gui.LayoutLabel chosen = aPaletteTileOtherThan(
                        (javax.swing.JPanel) paletteField.get(editor[0]), componentType.STRAIGHT);

                    if (chosen == null)
                    {
                        failure[0] = "no palette tile of a different type was found to place";
                        return;
                    }

                    componentType placing = chosen.getComponent().getType();

                    // THE GESTURE, in the order a mouse makes it.  First the palette: press, release, click;
                    // receiveClickEvent is what arms the tool, so all three are here.
                    editor[0].receiveMoveEvent(press(chosen), chosen);
                    editor[0].beginDrag(press(chosen), chosen);
                    editor[0].endDrag(press(chosen), chosen);
                    editor[0].receiveClickEvent(press(chosen), chosen);

                    // Then the diagram square.  The move event first, because endDrag asks where the pointer WAS
                    // (`getLastHoveredLabel`), not the release.
                    editor[0].receiveMoveEvent(press(square), square);
                    editor[0].beginDrag(press(square), square);
                    editor[0].endDrag(press(square), square);
                    editor[0].receiveClickEvent(press(square), square);

                    LayoutDiagramComponentType now = new LayoutDiagramComponentType(
                        diagram.getComponent(4, 2) == null
                            ? null : diagram.getComponent(4, 2).getType());

                    if (now.type != placing)
                    {
                        failure[0] = "the palette tile did not land on the occupied square.  Wanted "
                            + placing + " there, found " + now.type + ".  Adam, OB-169: \"clicking a "
                            + "tile in 'new components' followed by a square on the diagram no longer "
                            + "places that tile\"";
                        return;
                    }

                    // AND STILL IN PLACE MODE, which is the second half of what he asked for.
                    if (!editor[0].hasToolFlag())
                    {
                        failure[0] = "the tile was placed and place mode ended with it.  Adam: \"it "
                            + "should stay in place mode until escape is pressed or another action "
                            + "taken\"";
                    }
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });

            assertNull(failure[0], String.valueOf(failure[0]));
        }
        finally
        {
            // THE PREFERENCE COMES BACK WHATEVER ELSE HAPPENS: `sandbox.close()` puts `LAYOUT_OVERRIDE_PATH_PREF` back,
            // and a test that leaves a path behind changes which layout the application opens the next time Adam starts
            // it.  Its own `finally`, since `LayoutEditor.dispose()` does file IO under OneDrive and could throw.
            try
            {
                // THE EDITOR TOO, not only the window that owns it: an undisposed LayoutEditor keeps a frame, its grid
                // and its listeners alive for the rest of the JVM.
                if (editor[0] != null)
                {
                    javax.swing.SwingUtilities.invokeAndWait(() -> editor[0].dispose());
                }

                if (ui[0] != null)
                {
                    javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].dispose());
                }

                if (model != null) model.stop();
            }
            finally
            {
                if (sandbox != null) sandbox.close();
            }
        }
    }

    /** Carries a type through the lambda without needing a mutable box for it. */
    private static class LayoutDiagramComponentType
    {
        final componentType type;

        LayoutDiagramComponentType(componentType type)
        {
            this.type = type;
        }
    }

    /**
     * Any tile in the palette that is not the type already on the square (so a pass cannot mean "nothing happened");
     * null if there is none.
     */
    private static org.traincontrol.gui.LayoutLabel aPaletteTileOtherThan(javax.swing.JPanel palette,
        componentType avoid)
    {
        for (java.awt.Component child : palette.getComponents())
        {
            if (!(child instanceof org.traincontrol.gui.LayoutLabel)) continue;

            org.traincontrol.gui.LayoutLabel label = (org.traincontrol.gui.LayoutLabel) child;

            if (label.getComponent() != null && label.getComponent().getType() != avoid) return label;
        }

        return null;
    }

    /** A plain left button event on a label, which is all these handlers read off it. */
    private static java.awt.event.MouseEvent press(org.traincontrol.gui.LayoutLabel on)
    {
        return new java.awt.event.MouseEvent(on, java.awt.event.MouseEvent.MOUSE_PRESSED,
            0L, 0, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1);
    }
}
