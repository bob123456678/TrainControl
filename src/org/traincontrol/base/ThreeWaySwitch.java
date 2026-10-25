package org.traincontrol.base;

import java.util.ArrayList;
import java.util.List;

/**
 * A three-way point, as the pair of ordinary switch commands a route actually holds.
 *
 * A three-way is two motors at two consecutive addresses, and a route file says so only as a pair of Switch lines.
 * Setting one by hand means knowing which motor to move first, that the other has to be straight before it moves, and
 * that the first needs a moment to finish; getting one wrong throws the point through a position it was not asked for.
 * The three shapes are:
 *
 *   left        Switch B,straight,300   then   Switch A,turn
 *   straight    Switch A,straight,300   then   Switch B,straight
 *   right       Switch A,straight,300   then   Switch B,turn
 *
 * where A is the address the point is named by and B the one after it.  The motor that must end up straight is settled
 * FIRST, with a pause, and only then does the one that chooses the branch move.
 *
 * This class is the only place those three shapes are written down.  The editor shows a three-way as one row and asks
 * here what it is made of; reading a route back asks here whether a pair of lines is one.  Both directions are exact:
 * anything that does not match a shape exactly, down to the pause and the order, stays two ordinary rows, so a route
 * opened and saved unchanged comes back unchanged.
 */
public final class ThreeWaySwitch
{
    /** Which way the point is set: left and right as they read on the diagram, in the words the dropdown offers. */
    public enum Position
    {
        LEFT, STRAIGHT, RIGHT
    }

    /**
     * How long to wait after the first command, in milliseconds: long enough for a point motor to finish.  The default
     * for a new row, since slower motors need more.
     */
    public static final int SETTLE = 300;

    private final int address;

    private final Accessory.accessoryDecoderType protocol;

    private final Position position;

    private final int settle;

    private ThreeWaySwitch(int address, Accessory.accessoryDecoderType protocol, Position position,
        int settle)
    {
        this.address = address;
        this.protocol = protocol;
        this.position = position;
        this.settle = settle;
    }

    /** The address the point is named by, which is the lower of its two. */
    public int getAddress()
    {
        return address;
    }

    public Accessory.accessoryDecoderType getProtocol()
    {
        return protocol;
    }

    public Position getPosition()
    {
        return position;
    }

    /** The pause after the first command, in milliseconds. */
    public int getSettle()
    {
        return settle;
    }

    /** The two commands this point, in this position, is made of, in the order they must be sent. */
    public static List<RouteCommand> expand(int address, Accessory.accessoryDecoderType protocol,
        Position position, int settle)
    {
        List<RouteCommand> out = new ArrayList<>();

        Accessory.accessoryDecoderType speaks =
            protocol == null ? Accessory.DEFAULT_IMPLICIT_PROTOCOL : protocol;

        // Whichever motor has to be straight goes first, and waits: for left that is the SECOND address (left is chosen
        // by turning the first), for the other two the first.  The other order drives the point through the position in
        // between.
        int first = position == Position.LEFT ? address + 1 : address;
        int second = position == Position.LEFT ? address : address + 1;

        RouteCommand settles = RouteCommand.RouteCommandAccessory(first, speaks, false);

        settles.setDelay(Math.max(0, settle));

        out.add(settles);

        out.add(RouteCommand.RouteCommandAccessory(second, speaks,
            position != Position.STRAIGHT));

        return out;
    }

    /** The same, for a point that has been read back out of a route. */
    public List<RouteCommand> expand()
    {
        return expand(address, protocol, position, settle);
    }

    /**
     * Whether the two commands at this point in a route are a three-way, and which; null when they are not.
     *
     * Exact: both must be accessories on the same decoder, the first straight with a pause, the second with none, at
     * consecutive addresses in the arrangement one of the shapes calls for.  Anything else is two ordinary commands,
     * and reading them as a point would rewrite somebody's route the next time it was saved.  The shapes cannot be
     * confused: left has its turn one BELOW the straight, right one above, and straight has none.
     */
    public static ThreeWaySwitch read(List<RouteCommand> commands, int at)
    {
        if (commands == null || at < 0 || at + 1 >= commands.size()) return null;

        RouteCommand first = commands.get(at);
        RouteCommand second = commands.get(at + 1);

        if (first == null || second == null) return null;

        if (!first.isAccessory() || !second.isAccessory()) return null;

        // One point, one decoder: treating a mixed pair as one would write both out on whichever protocol the row
        // carried.
        if (first.getProtocol() != second.getProtocol()) return null;

        // The pause holds the second motor off until the first has finished; a pair without it was written for another
        // reason.
        if (first.getDelay() <= 0 || second.getDelay() != 0) return null;

        // The first is always the one being straightened
        if (first.getSetting()) return null;

        int straightAt = first.getAddress();
        int otherAt = second.getAddress();

        if (second.getSetting())
        {
            if (otherAt == straightAt - 1)
            {
                return new ThreeWaySwitch(otherAt, first.getProtocol(), Position.LEFT,
                    first.getDelay());
            }

            if (otherAt == straightAt + 1)
            {
                return new ThreeWaySwitch(straightAt, first.getProtocol(), Position.RIGHT,
                    first.getDelay());
            }

            return null;
        }

        if (otherAt == straightAt + 1)
        {
            return new ThreeWaySwitch(straightAt, first.getProtocol(), Position.STRAIGHT,
                first.getDelay());
        }

        return null;
    }

    /**
     * The word for a position, as the editor writes it: lower case and English, a stored value that the dropdown's
     * shown words sit over.
     */
    public static String wordFor(Position position)
    {
        return position == null ? "straight" : position.name().toLowerCase();
    }

    /** The position a word names, defaulting to straight. */
    public static Position positionFor(String word)
    {
        if (word == null) return Position.STRAIGHT;

        for (Position position : Position.values())
        {
            if (position.name().equalsIgnoreCase(word.trim())) return position;
        }

        return Position.STRAIGHT;
    }

    /** The words a three-way row offers, in the order they read on the diagram. */
    public static String[] words()
    {
        return new String[]{wordFor(Position.LEFT), wordFor(Position.STRAIGHT),
            wordFor(Position.RIGHT)};
    }
}
