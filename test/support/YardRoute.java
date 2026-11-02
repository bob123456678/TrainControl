package support;

import java.util.ArrayList;
import java.util.List;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * The addresses of a yard entry route on the layout the model has loaded: two switches, a signal, a three-way point and
 * the switch its conditions ask about, each a switch where the layout has no signal and the signal where it has one;
 * and the yard's lighting route that the yard route calls.
 */
public final class YardRoute
{
    public final int switch1;
    public final int switch2;
    public final int signal;
    public final int threeWay;
    public final int conditionSwitch;

    private YardRoute(MarklinControlStation model)
    {
        switch1 = RouteEditing.switchAt(model, 12);
        switch2 = RouteEditing.switchAt(model, switch1 + 1);
        threeWay = RouteEditing.switchAt(model, 30);
        conditionSwitch = RouteEditing.switchAt(model, 20);
        signal = RouteEditing.aSignal(model);
    }

    /** The addresses on this model's layout; refused where the layout has no signal or the point's second motor is one. */
    public static YardRoute on(MarklinControlStation model)
    {
        YardRoute yard = new YardRoute(model);

        if (yard.signal <= 0) throw new AssertionError("precondition: the layout has no signal, so no command can be one");

        if (RouteEditing.switchAt(model, yard.threeWay + 1) != yard.threeWay + 1)
        {
            throw new AssertionError("precondition: the three-way's second motor is a signal");
        }

        return yard;
    }

    /** Stores the lighting route the yard route calls: one switch, manual. */
    public static void addLights(MarklinControlStation model, String name)
    {
        List<RouteCommand> lights = new ArrayList<>();

        lights.add(RouteCommand.RouteCommandAccessory(RouteEditing.switchAt(model, 60),
            Accessory.accessoryDecoderType.MM2, true));

        if (!model.newRoute(name, lights, 0, Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null))
        {
            throw new AssertionError("precondition: the route " + name + " was not made");
        }
    }
}
