package support;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.RemoteDeviceCollection;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;

/**
 * Everything a sync makes or holds, one fact to a line in a fixed order: accessories and their positions, pages with
 * their IDs and tiles, routes with their lock, auto flag, trigger and every command, and locomotives with their address,
 * decoder, icon, function types and triggers, and multi-unit members.  The goldens of what a sync gives and the checks of
 * what a sync leaves alone both read it.
 *
 * @author Adam
 */
public final class HeldState
{
    private HeldState()
    {
    }

    public static String of(MarklinControlStation model) throws Exception
    {
        StringBuilder out = new StringBuilder();
        dump(model, out);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    public static void dump(MarklinControlStation model, StringBuilder out) throws Exception
    {
        java.lang.reflect.Field f = MarklinControlStation.class.getDeclaredField("accDB");
        f.setAccessible(true);
        RemoteDeviceCollection<MarklinAccessory, Integer> accDB =
            (RemoteDeviceCollection<MarklinAccessory, Integer>) f.get(model);

        section(out, "accessories", accDB.getItems().stream().map(a -> "acc uid=" + Integer.toHexString(a.getUID())
            + " name=" + a.getName() + " " + a.getType() + " " + a.getDecoderType() + " switched=" + a.isSwitched()));
        out.append("-- pages ").append(model.getLayoutList()).append("\n");

        for (String name : model.getLayoutListByName())
        {
            LayoutDiagram d = model.getLayout(name);
            out.append("page ").append(name).append(" id=").append(d.getPageId()).append(" unreadable=")
                .append(d.isUnreadable()).append("\n");

            for (LayoutDiagramComponent c : d.getAll())
            {
                MarklinAccessory held = (MarklinAccessory) c.getAccessory();
                out.append("  (" + c.getX() + "," + c.getY() + ") " + c.getType() + " addr=" + c.getAddress() + " prot="
                    + c.getProtocol() + (held == null ? "" : " acc=" + held.getName() + "/" + held.isSwitched()) + "\n");
            }
        }

        section(out, "routes", model.getRoutes().stream().map(HeldState::route));
        section(out, "locomotives", model.getLocomotives().stream().map(l -> locomotive((MarklinLocomotive) l)));
    }

    private static String route(MarklinRoute r)
    {
        try
        {
            return "route " + r.toString().replace("\n", " | ") + " | locked=" + r.isLocked() + " | " + r.toJSON();
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static String locomotive(MarklinLocomotive l)
    {
        return "loc " + l.getName() + " addr=" + l.getAddress() + " " + l.getDecoderType() + " uid=" + l.getUID()
            + " image=" + l.getImageURL() + " f=" + l.getNumF() + " types=" + Arrays.toString(l.getFunctionTypes())
            + " triggers=" + Arrays.toString(l.getFunctionTriggerTypes()) + " members="
            + (l.getModelMultiUnitLocomotiveNames() == null ? "-" : new java.util.TreeMap<>(l.getModelMultiUnitLocomotiveNames()));
    }

    /** A heading with its count, then the lines in sorted order. */
    private static void section(StringBuilder out, String heading, Stream<String> lines)
    {
        List<String> sorted = lines.sorted().collect(Collectors.toList());
        out.append("-- ").append(heading).append(" ").append(sorted.size()).append("\n");
        sorted.forEach(line -> out.append("  ").append(line).append("\n"));
    }
}
