package org.traincontrol.base;

import java.util.ArrayList;
import java.util.List;

/**
 * A route command as three things a person picks, rather than a line they have to type.
 *
 * The editable shape of every command TrainControl understands turns out to be the same three columns:
 * what KIND of thing this is, WHICH one, and what to do to it. An accessory is "Accessory / 12 /
 * turn"; a locomotive speed is "Speed / BR 628 / 40"; a stop is "Stop" and nothing else. Three columns
 * of dropdowns cover all of it, which is what lets the editor be a table instead of a text box with
 * syntax rules.
 *
 * The point of this class is that the conversion can be tested. A route edited through controls is
 * only safe if a command taken apart into columns and put back together is the command it started as -
 * otherwise opening a route and pressing Save would quietly change it.
 *
 * A kind this does not know is not lost. {@link #of} answers null, and the editor keeps that command
 * as it found it - the same rule the condition rows follow for a bracket.
 */
public final class CommandRow
{
    /**
     * What a command does, as the editor offers it.
     *
     * Deliberately not RouteCommand.commandType: that enum carries kinds the editor has no controls
     * for, and mapping them here rather than exposing them keeps "what can be edited" in one place.
     */
    public enum Kind
    {
        ACCESSORY,
        FEEDBACK,
        FUNCTION,
        LOCOMOTIVE_SPEED,
        LOCOMOTIVE_DIRECTION,
        STOP,
        FUNCTIONS_OFF
    }

    private final Kind kind;

    /** The accessory address, or the locomotive name.  Empty where the kind has no target. */
    private final String target;

    /** On/off, a speed, a function number, or a direction.  Empty where the kind has no setting. */
    private final String setting;

    /**
     * The decoder type of an accessory, or null for every other kind.
     *
     * Carried rather than assumed because MM2 and DCC are SEPARATE address spaces - MarklinAccessory
     * puts them at different UIDs - so a DCC turnout saved as MM2 does not fail to move, it moves a
     * different piece of railway, or invents a phantom one.  The editor had no column for this and
     * defaulted every accessory to MM2, which rewrote DCC routes on Save.
     */
    private final Accessory.accessoryDecoderType protocol;

    /**
     * How long to wait after this command, in milliseconds.  Zero means no wait.
     *
     * A layout timed so a slow point motor settles before the next command, or so two motors never
     * draw at once, is held together by these numbers and by nothing else on screen.
     */
    private final int delay;

    public CommandRow(Kind kind, String target, String setting)
    {
        this(kind, target, setting, null, 0);
    }

    public CommandRow(Kind kind, String target, String setting,
        Accessory.accessoryDecoderType protocol, int delay)
    {
        this.kind = kind;
        this.target = target == null ? "" : target;
        this.setting = setting == null ? "" : setting;
        this.protocol = protocol;
        this.delay = delay < 0 ? 0 : delay;
    }

    public Kind getKind()
    {
        return kind;
    }

    public String getTarget()
    {
        return target;
    }

    public String getSetting()
    {
        return setting;
    }

    /**
     * The accessory's decoder type, or null where the kind has none.
     */
    public Accessory.accessoryDecoderType getProtocol()
    {
        return protocol;
    }

    public int getDelay()
    {
        return delay;
    }

    /**
     * Whether this kind means anything as a CONDITION.
     *
     * Route.evaluate understands accessory and feedback terms and nothing else - so a condition built
     * from any other kind is permanently false, and a route carrying one silently stops firing with
     * no error anywhere.  The condition editor offered all seven kinds, which made that a two-click
     * mistake.
     */
    public static boolean canBeACondition(Kind kind)
    {
        return kind == Kind.ACCESSORY || kind == Kind.FEEDBACK;
    }

    /**
     * Whether this kind can carry a decoder type, so the editor can grey the cell for the rest.
     */
    public static boolean hasProtocol(Kind kind)
    {
        return kind == Kind.ACCESSORY;
    }

    /**
     * Whether this kind can carry a delay.  Feedback, stop and functions-off cannot - RouteCommand
     * only writes a delay for accessory, speed, direction and function commands.
     */
    public static boolean hasDelay(Kind kind)
    {
        return kind == Kind.ACCESSORY || kind == Kind.LOCOMOTIVE_SPEED
            || kind == Kind.LOCOMOTIVE_DIRECTION || kind == Kind.FUNCTION;
    }

    /**
     * Whether this kind takes a target at all, so the editor can grey the cell rather than offer a box
     * that does nothing.
     */
    public static boolean hasTarget(Kind kind)
    {
        return kind != Kind.STOP && kind != Kind.FUNCTIONS_OFF;
    }

    /**
     * Whether this kind takes a setting.
     */
    public static boolean hasSetting(Kind kind)
    {
        return kind != Kind.STOP && kind != Kind.FUNCTIONS_OFF;
    }

    /**
     * Whether the FUNCTION kind's extra number is needed - it is the only kind with three parts, so it
     * borrows the target for the locomotive and packs "function:setting" into the setting.
     */
    public static boolean isFunction(Kind kind)
    {
        return kind == Kind.FUNCTION;
    }

    /**
     * The row behind a command, or null when the editor has no controls for it.
     *
     * @param command the command as stored
     */
    public static CommandRow of(RouteCommand command)
    {
        if (command == null) return null;

        if (command.isAccessory())
        {
            return new CommandRow(Kind.ACCESSORY, String.valueOf(command.getAddress()),
                command.getSetting() ? "turn" : "straight", command.getProtocol(), command.getDelay());
        }

        if (command.isFeedback())
        {
            return new CommandRow(Kind.FEEDBACK, String.valueOf(command.getAddress()),
                command.getSetting() ? "on" : "off");
        }

        if (command.isFunction())
        {
            // The only kind with three parts, so the function number rides with its setting
            return new CommandRow(Kind.FUNCTION, command.getName(),
                command.getFunction() + ":" + (command.getSetting() ? "on" : "off"),
                null, command.getDelay());
        }

        if (command.isLocomotiveSpeed())
        {
            return new CommandRow(Kind.LOCOMOTIVE_SPEED, command.getName(),
                String.valueOf(command.getSpeed()), null, command.getDelay());
        }

        if (command.isLocomotiveDirection())
        {
            return new CommandRow(Kind.LOCOMOTIVE_DIRECTION, command.getName(),
                command.getDirection() == Locomotive.locDirection.DIR_FORWARD ? "forward" : "backward",
                null, command.getDelay());
        }

        if (command.isStop()) return new CommandRow(Kind.STOP, "", "");

        if (command.isFunctionsOff()) return new CommandRow(Kind.FUNCTIONS_OFF, "", "");

        // A route, an auto-locomotive, lights - kinds with no controls yet.  Answering null keeps the
        // command exactly as it was found rather than losing it.
        return null;
    }

    /**
     * The command this row means, with the decoder type and delay the row carries.
     */
    public RouteCommand toCommand()
    {
        return toCommand(Accessory.DEFAULT_IMPLICIT_PROTOCOL);
    }

    /**
     * The command this row means.
     *
     * @param protocol the decoder type to give an accessory that does not carry one of its own - a row
     *        the user built from scratch.  A row read from an existing command keeps ITS protocol, so
     *        opening a DCC route and saving it cannot silently move it to MM2.
     * @throws IllegalArgumentException when the row cannot be made into a command, so the editor can
     *         say which row is wrong instead of saving a broken route
     */
    public RouteCommand toCommand(Accessory.accessoryDecoderType protocol)
    {
        RouteCommand command = build(this.protocol != null ? this.protocol
            : (protocol != null ? protocol : Accessory.DEFAULT_IMPLICIT_PROTOCOL));

        // Set only when there is one: "no delay" is the absence of the key, and writing a zero makes a
        // second representation of the same command that compares unequal to the first
        if (delay > 0) command.setDelay(delay);

        return command;
    }

    private RouteCommand build(Accessory.accessoryDecoderType protocol)
    {
        switch (kind)
        {
            case STOP:
                return RouteCommand.RouteCommandStop();

            case FUNCTIONS_OFF:
                return RouteCommand.RouteCommandFunctionsOff();

            case ACCESSORY:
                return RouteCommand.RouteCommandAccessory(number(target, "address"), protocol,
                    "turn".equalsIgnoreCase(setting));

            case FEEDBACK:
                return RouteCommand.RouteCommandFeedback(number(target, "address"),
                    "on".equalsIgnoreCase(setting));

            case LOCOMOTIVE_SPEED:
                requireName();
                return RouteCommand.RouteCommandLocomotiveSpeed(target, number(setting, "speed"));

            case LOCOMOTIVE_DIRECTION:
                requireName();
                return RouteCommand.RouteCommandLocomotiveDirection(target,
                    "backward".equalsIgnoreCase(setting)
                        ? Locomotive.locDirection.DIR_BACKWARD
                        : Locomotive.locDirection.DIR_FORWARD);

            case FUNCTION:
            {
                requireName();

                int colon = setting.indexOf(':');

                if (colon < 0)
                {
                    throw new IllegalArgumentException(
                        "a function needs a number and a setting, such as 4:on");
                }

                return RouteCommand.RouteCommandFunction(target,
                    number(setting.substring(0, colon), "function number"),
                    "on".equalsIgnoreCase(setting.substring(colon + 1)));
            }

            default:
                throw new IllegalArgumentException("no controls for " + kind);
        }
    }

    private void requireName()
    {
        if (target.trim().isEmpty())
        {
            throw new IllegalArgumentException("this row needs a locomotive");
        }
    }

    private static int number(String text, String what)
    {
        try
        {
            return Integer.parseInt(text.trim());
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException(
                "\"" + text + "\" is not a " + what);
        }
    }

    /**
     * The rows behind a list of commands, with an entry per command.
     *
     * A command with no controls yields a null entry, so the caller keeps the original at that
     * position rather than dropping it.
     */
    public static List<CommandRow> of(List<RouteCommand> commands)
    {
        List<CommandRow> rows = new ArrayList<>();

        if (commands == null) return rows;

        for (RouteCommand command : commands) rows.add(of(command));

        return rows;
    }

    @Override
    public String toString()
    {
        return kind + (target.isEmpty() ? "" : " " + target) + (setting.isEmpty() ? "" : " " + setting);
    }
}
