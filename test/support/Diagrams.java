package support;

import java.io.IOException;
import java.util.List;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.marklin.MarklinAccessory;

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

    /**
     * Attaches an accessory of its own to every addressed switch and signal, as syncLayouts does on loading: pages
     * parsed without a model carry addresses and no Accessory, and the reduction cannot trace through such a switch.
     */
    public static void wireAccessories(List<LayoutDiagram> pages)
    {
        for (LayoutDiagram page : pages)
        {
            for (LayoutDiagramComponent component : page.getAll())
            {
                if (!component.isSwitch() && !component.isSignal()) continue;

                // the application skips tiles drawn without a digital address, and so does this
                if (component.getAddress() <= 0) continue;

                Accessory.accessoryType type = component.isSignal()
                    ? Accessory.accessoryType.SIGNAL : Accessory.accessoryType.SWITCH;

                component.setAccessory(accessory(component.getAddress(), type, component.getProtocol()));

                if (component.isThreeWay())
                {
                    component.setAccessory2(accessory(component.getAddress() + 1,
                        Accessory.accessoryType.SWITCH, component.getProtocol()));
                }
            }
        }
    }

    /** An accessory at this logical address, named exactly as the application names it. */
    public static MarklinAccessory accessory(int logicalAddress, Accessory.accessoryType type,
        accessoryDecoderType protocol)
    {
        return new MarklinAccessory(null, logicalAddress - 1, type, protocol,
            MarklinAccessory.getNameWithProtocol(logicalAddress, type, protocol), false, 0);
    }
}
