package org.traincontrol.base;

import java.util.ArrayList;
import java.util.List;

/**
 * A three-way point, as the pair of ordinary switch commands a route actually holds.
 *
 * A three-way is two motors at two consecutive addresses, and there is nothing in a route file that
 * says so - it is a pair of Switch lines like any other pair.  Setting one by hand means knowing
 * which motor to move first, that the other has to be straight before it moves, and that the first
 * needs a moment to finish; getting any of the three wrong throws the point through a position it
 * was not asked for, or leaves both motors fighting.  The three shapes are:
 *
 *   left        Switch B,straight,300   then   Switch A,turn
 *   straight    Switch A,straight,300   then   Switch B,straight
 *   right       Switch A,straight,300   then   Switch B,turn
 *
 * where A is the address the point is named by and B is the one after it.  The order is the whole
 * point: the motor that must end up straight is settled FIRST, with a pause, and only then does the
 * one that chooses the branch move.
 *
 * This class is the only place those three shapes are written down.  The editor shows a three-way as
 * one row and asks here what it is made of; reading a route back asks here whether a pair of lines
 * is one.  Both directions are exact - anything that does not match one of the three shapes exactly,
 * down to the pause and the order, stays two ordinary rows rather than being guessed at - so a route
 * opened and saved unchanged comes back unchanged, which is the property the editor rests on and the
 * one a translation layer gets wrong.
 */
public final class ThreeWaySwitch
{
    /**
     * Which way the point is set.
     *
     * Left and right are as they read on the diagram, and straight is straight; the words are the
     * ones the dropdown offers, so the row, the reading and the file cannot disagree.
     */
    public enum Position
    {
        LEFT, STRAIGHT, RIGHT
    }

    /**
     * How long to wait after the first command, in milliseconds.
     *
     * Long enough for a point motor to finish before the second one starts.  Kept as the default for
     * a new row rather than as a fixed rule: the number belongs to the ironwork, and a layout with
     * slower motors needs a bigger one.
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

    /**
     * @return the address the point is named by, which is the lower of its two
     */
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

    /**
     * @return the pause after the first command, in milliseconds
     */
    public int getSettle()
    {
        return settle;
    }

    /**
     * The two commands this point, in this position, is made of.
     *
     * @param address the lower of the two addresses
     * @param protocol which decoder speaks to both motors
     * @param position which way to set it
     * @param settle the pause after the first command
     * @return the two commands, in the order they must be sent
     */
    public static List<RouteCommand> expand(int address, Accessory.accessoryDecoderType protocol,
        Position position, int settle)
    {
        List<RouteCommand> out = new ArrayList<>();

        Accessory.accessoryDecoderType speaks =
            protocol == null ? Accessory.DEFAULT_IMPLICIT_PROTOCOL : protocol;

        // Whichever motor has to be straight goes first, and waits.
        //
        // For left that is the SECOND address, because left is chosen by turning the first; for the
        // other two it is the first, because both are chosen on the second.  Sending them the other
        // way round drives the point through the position in between on its way, which is a train
        // over a point that is moving under it.
        int first = position == Position.LEFT ? address + 1 : address;
        int second = position == Position.LEFT ? address : address + 1;

        RouteCommand settles = RouteCommand.RouteCommandAccessory(first, speaks, false);

        settles.setDelay(Math.max(0, settle));

        out.add(settles);

        out.add(RouteCommand.RouteCommandAccessory(second, speaks,
            position != Position.STRAIGHT));

        return out;
    }

    /**
     * The same, for a point that has been read back out of a route.
     */
    public List<RouteCommand> expand()
    {
        return expand(address, protocol, position, settle);
    }

    /**
     * Whether the two commands at this point in a route are a three-way, and which.
     *
     * Exact.  Both commands must be accessories on the same decoder, the first must be straight and
     * must carry a pause, the second must carry none, and the two addresses must be consecutive in
     * the arrangement one of the three shapes calls for.  Anything else is two ordinary commands
     * that happen to be next to each other, and reading them as a point would rewrite somebody's
     * route into a different one the next time it was saved.
     *
     * The three shapes cannot be confused with one another: left has its turn at one BELOW the
     * straight, right has it one above, and straight has no turn at all.
     *
     * @param commands the route
     * @param at where to look
     * @return the point, or null when the two commands there are not one
     */
    public static ThreeWaySwitch read(List<RouteCommand> commands, int at)
    {
        if (commands == null || at < 0 || at + 1 >= commands.size()) return null;

        RouteCommand first = commands.get(at);
        RouteCommand second = commands.get(at + 1);

        if (first == null || second == null) return null;

        if (!first.isAccessory() || !second.isAccessory()) return null;

        // One point, one decoder.  Two motors of a three-way answering to different protocols is not
        // a thing that exists, and treating a mixed pair as one would write both out on whichever
        // protocol the row happened to carry - a different piece of railway, silently.
        if (first.getProtocol() != second.getProtocol()) return null;

        // The pause is not decoration: it is what holds the second motor off until the first has
        // finished, and a pair without it is a pair somebody wrote for some other reason.
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
     * The word for a position, as the editor writes it in a row.
     *
     * Lower case and in English, like the settings of every other kind - these are stored values
     * rather than anything shown, and the dropdown puts the shown words over them.
     */
    public static String wordFor(Position position)
    {
        return position == null ? "straight" : position.name().toLowerCase();
    }

    /**
     * The position a word names, defaulting to straight.
     *
     * @param word what the row is carrying
     * @return the position
     */
    public static Position positionFor(String word)
    {
        if (word == null) return Position.STRAIGHT;

        for (Position position : Position.values())
        {
            if (position.name().equalsIgnoreCase(word.trim())) return position;
        }

        return Position.STRAIGHT;
    }

    /**
     * The words a three-way row offers, in the order they read on the diagram.
     */
    public static String[] words()
    {
        return new String[]{wordFor(Position.LEFT), wordFor(Position.STRAIGHT),
            wordFor(Position.RIGHT)};
    }
}
