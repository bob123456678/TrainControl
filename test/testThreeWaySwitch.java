import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.base.ThreeWaySwitch;

/**
 * A three-way point, as the pair of ordinary switch commands a route actually holds.
 *
 * There is nothing in a route file that says two Switch lines are one point.  Adam wrote the three
 * shapes down:
 *
 *   left        Switch 2,straight,300   then   Switch 1,turn
 *   straight    Switch 1,straight,300   then   Switch 2,straight
 *   right       Switch 1,straight,300   then   Switch 2,turn
 *
 * and said the sequence is deliberate.  It is: the motor that has to end up straight is settled
 * first and given a moment, and only then does the one that chooses the branch move.  Sending them
 * the other way round drives the point through the position in between on its way.
 *
 * What these tests are really about is the round trip.  An editor that shows two lines as one row
 * has to be able to put the row back exactly as it found it, or opening a route and saving it
 * unchanged rewrites somebody's railway - and it does so quietly, which is the failure a translation
 * layer is for.
 */
public class testThreeWaySwitch
{
    /**
     * Each of the three shapes is what Adam wrote down.
     */
    @Test
    public void testTheThreeShapesAreTheOnesThatWereSpecified()
    {
        assertEquals(lines(ThreeWaySwitch.Position.LEFT), "2 straight 300 | 1 turn 0",
            "left settles the SECOND address and then turns the first");

        assertEquals(lines(ThreeWaySwitch.Position.STRAIGHT), "1 straight 300 | 2 straight 0",
            "straight settles the first and then straightens the second");

        assertEquals(lines(ThreeWaySwitch.Position.RIGHT), "1 straight 300 | 2 turn 0",
            "right settles the first and then turns the second");
    }

    /**
     * The pause is on the FIRST command, which is the whole reason the order matters.
     */
    @Test
    public void testThePauseIsOnTheCommandThatGoesFirst()
    {
        for (ThreeWaySwitch.Position position : ThreeWaySwitch.Position.values())
        {
            List<RouteCommand> made = ThreeWaySwitch.expand(1,
                Accessory.accessoryDecoderType.MM2, position, ThreeWaySwitch.SETTLE);

            assertEquals(made.get(0).getDelay(), ThreeWaySwitch.SETTLE,
                position + ": the first motor is not given time to finish, so the second starts "
                + "while the point is still moving");

            assertEquals(made.get(1).getDelay(), 0, position + ": a pause after the pair holds up "
                + "the command behind it for a reason that is not this point's business");
        }
    }

    /**
     * Every position survives being written out and read back.
     *
     * The property the editor rests on: a row becomes two lines, and those two lines become the same
     * row again.  Anything less and opening a route and saving it moves the railway.
     */
    @Test
    public void testEveryPositionComesBackAsItself()
    {
        for (ThreeWaySwitch.Position position : ThreeWaySwitch.Position.values())
        {
            List<RouteCommand> made = ThreeWaySwitch.expand(7,
                Accessory.accessoryDecoderType.MM2, position, ThreeWaySwitch.SETTLE);

            ThreeWaySwitch read = ThreeWaySwitch.read(made, 0);

            assertNotNull(read, position + ": a point this class wrote could not be read back");

            assertEquals(read.getPosition(), position, "it came back as a different position");

            assertEquals(read.getAddress(), 7,
                "it came back naming a different point, which would move a different piece of "
                + "railway on the next save");

            assertEquals(read.getSettle(), ThreeWaySwitch.SETTLE, "and with a different pause");

            assertEquals(describe(read.expand()), describe(made),
                "the two lines are not the two lines it was read from - a route opened and saved "
                + "unchanged would come back changed");
        }
    }

    /**
     * The three shapes cannot be mistaken for one another.
     *
     * Left has its turn one BELOW the straightened motor and right has it one above, so no pair can
     * be read two ways.  Were that not so, one of the three would open as another and the point
     * would be set the wrong way the next time the route ran.
     */
    @Test
    public void testOneShapeIsNeverReadAsAnother()
    {
        for (ThreeWaySwitch.Position position : ThreeWaySwitch.Position.values())
        {
            ThreeWaySwitch read = ThreeWaySwitch.read(ThreeWaySwitch.expand(4,
                Accessory.accessoryDecoderType.MM2, position, 300), 0);

            assertEquals(read.getPosition(), position, "expanded " + position + " and read back "
                + read.getPosition());
        }
    }

    /**
     * A pair that is not one of the shapes stays two ordinary commands.
     *
     * The half that protects everything already on somebody's layout.  Two switch commands next to
     * each other are the commonest thing in a route, and rolling an unrecognised pair up would show
     * the user a point that is not there and write a different pair back out on the next save.
     */
    @Test
    public void testAnythingElseIsLeftAlone()
    {
        assertNull(ThreeWaySwitch.read(pair(
            accessory(1, false, 0), accessory(2, false, 0)), 0),
            "no pause on the first, so nothing says these two belong together");

        assertNull(ThreeWaySwitch.read(pair(
            accessory(1, true, 300), accessory(2, false, 0)), 0),
            "the first is turned rather than straightened, which is no shape this class writes");

        assertNull(ThreeWaySwitch.read(pair(
            accessory(1, false, 300), accessory(9, true, 0)), 0),
            "the addresses are not consecutive, so they are two different points");

        assertNull(ThreeWaySwitch.read(pair(
            accessory(1, false, 300), accessory(2, false, 250)), 0),
            "a pause after the second belongs to the command behind it, not to this point");

        assertNull(ThreeWaySwitch.read(pair(
            RouteCommand.RouteCommandAccessory(1, Accessory.accessoryDecoderType.MM2, false),
            RouteCommand.RouteCommandAccessory(2, Accessory.accessoryDecoderType.DCC, false)), 0),
            "two motors of one point do not answer to two different decoders - and MM2 and DCC are "
            + "separate address spaces, so writing the pair back on one of them would move a "
            + "different piece of railway");

        assertNull(ThreeWaySwitch.read(pair(
            accessory(1, false, 300), RouteCommand.RouteCommandFeedback(2, true)), 0),
            "a sensor is not half of a point");

        assertNull(ThreeWaySwitch.read(
            pair(accessory(1, false, 300), accessory(2, true, 0)), 1),
            "there is nothing after the last command to pair with");
    }

    /**
     * A pause other than the usual one is carried rather than replaced.
     *
     * A layout with slower motors needs a bigger number, and a translation layer that normalised it
     * to three hundred would undo that every time the route was opened.
     */
    @Test
    public void testAnUnusualPauseIsKept()
    {
        List<RouteCommand> made = ThreeWaySwitch.expand(3,
            Accessory.accessoryDecoderType.MM2, ThreeWaySwitch.Position.RIGHT, 900);

        ThreeWaySwitch read = ThreeWaySwitch.read(made, 0);

        assertEquals(read.getSettle(), 900, "the layout's own timing was replaced with a default");

        assertEquals(describe(read.expand()), describe(made), "and the lines changed with it");
    }

    /**
     * The words a row carries mean the positions they name, whatever case they arrive in.
     */
    @Test
    public void testTheWordsAndThePositionsAgree()
    {
        for (ThreeWaySwitch.Position position : ThreeWaySwitch.Position.values())
        {
            assertEquals(ThreeWaySwitch.positionFor(ThreeWaySwitch.wordFor(position)), position,
                "a position did not survive being written as a word and read back");
        }

        assertEquals(ThreeWaySwitch.positionFor("LEFT"), ThreeWaySwitch.Position.LEFT);

        assertEquals(ThreeWaySwitch.positionFor(null), ThreeWaySwitch.Position.STRAIGHT,
            "a row with nothing in its setting yet is straight, which is the harmless one");

        assertEquals(ThreeWaySwitch.positionFor("sideways"), ThreeWaySwitch.Position.STRAIGHT,
            "and so is a word this class does not know");
    }

    private static String lines(ThreeWaySwitch.Position position)
    {
        return describe(ThreeWaySwitch.expand(1, Accessory.accessoryDecoderType.MM2, position,
            ThreeWaySwitch.SETTLE));
    }

    /**
     * The two commands as "address setting pause | address setting pause".
     */
    private static String describe(List<RouteCommand> commands)
    {
        StringBuilder out = new StringBuilder();

        for (RouteCommand command : commands)
        {
            if (out.length() > 0) out.append(" | ");

            out.append(command.getAddress())
               .append(command.getSetting() ? " turn " : " straight ")
               .append(command.getDelay());
        }

        return out.toString();
    }

    private static RouteCommand accessory(int address, boolean setting, int delay)
    {
        RouteCommand out = RouteCommand.RouteCommandAccessory(address,
            Accessory.accessoryDecoderType.MM2, setting);

        out.setDelay(delay);

        return out;
    }

    private static List<RouteCommand> pair(RouteCommand first, RouteCommand second)
    {
        List<RouteCommand> out = new ArrayList<>();

        out.add(first);
        out.add(second);

        return out;
    }
}
