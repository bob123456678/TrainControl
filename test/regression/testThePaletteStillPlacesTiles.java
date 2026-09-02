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
 * Adam, OB-169: **"clicking a tile in 'new components' followed by a square on the diagram no longer
 * places that tile.  it should place it and stay in place mode until escape is pressed or another
 * action taken."**
 *
 * **The square he was clicking already had track on it**, and that is the whole of it.  Pressing the
 * mouse on a square that carries a component is how a tile is picked up to be dragged, so the press
 * handler arms a MOVE and remembers that square as the drag source - throwing away the palette
 * selection the user had just made.  The release then sees the press and the release on the same
 * square, calls it a click rather than a drag, and clears the clipboard; by the time the click event
 * arrives there is no tool left to run and nothing happens.
 *
 * On an EMPTY square the same gesture works, because a press there picks nothing up: the palette
 * selection survives to the click.  That is why this went unnoticed, and it is why the test places
 * over occupied track specifically.
 *
 * The gesture is driven through the editor's own three handlers in the order a mouse produces them -
 * beginDrag, endDrag, receiveClickEvent - rather than through executeTool, because executeTool was
 * never the broken part.  Calling it directly passes on the code that shipped.
 */
public class testThePaletteStillPlacesTiles
{
    /**
     * A tile from the palette lands on a square that already has track on it.
     *
     * MUTATION: putting back the press handler's unconditional `initCopy(label, null, true)` - the
     * pick-up-to-drag arm - fails this, which is the shipped behaviour it was written against.
     */
    @Test
    public void testAPaletteTileLandsOnOccupiedTrack() throws Exception
    {
        final org.traincontrol.gui.TrainControlUI[] ui = new org.traincontrol.gui.TrainControlUI[1];
        final org.traincontrol.gui.LayoutEditor[] editor = new org.traincontrol.gui.LayoutEditor[1];

        support.LayoutSandbox sandbox = null;
        org.traincontrol.marklin.MarklinControlStation model = null;

        try
        {
            // Before the model, not just before the window (OB-111) - constructing a TrainControlUI
            // reads the layout-path preference, and without the sandbox it is Adam's own railway.
            sandbox = support.LayoutSandbox.open();

            model = org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

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

            // THE SQUARE IS OCCUPIED, which is the case that fails.  A straight, so that the tile
            // arriving from the palette is a different type and the assertion cannot pass by accident.
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
                editor[0] = new org.traincontrol.gui.LayoutEditor(diagram, 30, ui[0], 0));

            java.lang.reflect.Method drawGrid =
                org.traincontrol.gui.LayoutEditor.class.getDeclaredMethod("drawGrid");
            drawGrid.setAccessible(true);

            java.lang.reflect.Field gridField =
                org.traincontrol.gui.LayoutEditor.class.getDeclaredField("grid");
            gridField.setAccessible(true);

            java.lang.reflect.Field paletteField =
                org.traincontrol.gui.LayoutEditor.class.getDeclaredField("newComponents");
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

                    // THE GESTURE, in the order a mouse makes it.
                    //
                    // First the palette: press, release, click.  The release over the palette is what
                    // arms nothing - receiveClickEvent is what arms the tool, which is why all three
                    // are here rather than just the click.
                    editor[0].receiveMoveEvent(press(chosen), chosen);
                    editor[0].beginDrag(press(chosen), chosen);
                    editor[0].endDrag(press(chosen), chosen);
                    editor[0].receiveClickEvent(press(chosen), chosen);

                    // Then the diagram square.  The move event first, because endDrag asks where the
                    // pointer WAS - `getLastHoveredLabel` - rather than reading it off the release,
                    // so without a hover the release lands on whatever was hovered last.
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
            if (ui[0] != null)
            {
                javax.swing.SwingUtilities.invokeAndWait(() -> ui[0].dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
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
     * Any tile in the palette that is not the type already on the square.
     *
     * @param palette the "new components" panel
     * @param avoid the type the fixture already has, so a pass cannot mean "nothing happened"
     * @return a palette label, or null if the palette holds nothing else
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
