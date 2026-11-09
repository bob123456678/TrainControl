package support;

import java.io.IOException;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/** The plain squares a test draws a page of track with: MM2, no text, state 0. */
public final class Diagrams
{
    private Diagrams()
    {
    }

    /** A straight at (x, y), unturned. */
    public static void straight(LayoutDiagram page, int x, int y) throws IOException
    {
        straight(page, x, y, 0);
    }

    /** A straight at (x, y), turned to this orientation. */
    public static void straight(LayoutDiagram page, int x, int y, int orientation) throws IOException
    {
        page.addComponent(componentType.STRAIGHT, x, y, orientation, 0, 0, 0, accessoryDecoderType.MM2, null);
    }

    /**
     * Unturned squares eastward from (x, y), one per letter: S a straight, F a feedback whose address and raw address
     * are the next of `addresses`.
     */
    public static void row(LayoutDiagram page, int x, int y, String squares, int... addresses) throws IOException
    {
        int next = 0;

        for (char square : squares.toCharArray())
        {
            if (square == 'F') feedback(page, x++, y, addresses[next], addresses[next++]);
            else straight(page, x++, y);
        }
    }

    /** An unturned feedback (sensor) square at (x, y) with this address and raw address. */
    public static void feedback(LayoutDiagram page, int x, int y, int address, int raw) throws IOException
    {
        page.addComponent(componentType.FEEDBACK, x, y, 0, 0, address, raw, accessoryDecoderType.MM2, null);
    }
}
