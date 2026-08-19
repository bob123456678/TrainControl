import java.awt.GraphicsEnvironment;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.CommandRow;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Capturing a route by working the railway, which is the route editor's most useful trick.
 *
 * Rather than looking up addresses, the user ticks a box, throws the switches and signals in the order
 * they want them, and watches the route write itself.  What actually happens is that every accessory
 * change produces a setting line, and the open editor is handed it to parse.
 *
 * There are two editors now, and both take that same line - so what has to hold is that the line an
 * accessory produces is one the parser understands, and that the new editor turns it into the right
 * row.  A capture that silently produced nothing, or the wrong address, would be a route that looks
 * built and does something else.
 */
public class testRouteCapture
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);
        model.stop();
    }

    /**
     * What a thrown accessory produces is what the parser reads.
     *
     * The seam capture rests on.  Both editors are handed toAccessorySettingString and both call
     * fromLine on it, so if those two ever stop agreeing, capture silently stops working - the box
     * stays ticked, switches get thrown, and nothing appears.
     */
    @Test
    public void testAThrownAccessoryProducesALineTheParserUnderstands() throws Exception
    {
        MarklinAccessory signal = model.newSignal(71, Accessory.accessoryDecoderType.MM2, false);

        signal.setSwitched(true);

        String captured = signal.toAccessorySettingString();

        assertFalse(captured.trim().isEmpty(), "a thrown accessory produced nothing to capture");

        RouteCommand parsed = RouteCommand.fromLine(captured, false);

        assertNotNull(parsed, "the line an accessory produces cannot be parsed back: " + captured);

        assertTrue(parsed.isAccessory(), "a captured accessory parsed as something else: " + captured);

        assertEquals(parsed.getAddress(), signal.getAddress() + 1,
            "the captured command names a different accessory from the one that was thrown - the line "
            + "carries the LOGICAL address, one above the raw one");
    }

    /**
     * A captured line becomes a row the new editor can show.
     *
     * The new editor keeps commands as rows rather than text, so a captured line has to survive one
     * more step than it used to.  If it does not, capture appears to do nothing.
     */
    @Test
    public void testACapturedLineBecomesAnEditableRow() throws Exception
    {
        MarklinAccessory turnout = model.newSwitch(72, Accessory.accessoryDecoderType.MM2, false);

        turnout.setSwitched(true);

        RouteCommand parsed = RouteCommand.fromLine(turnout.toAccessorySettingString(), false);

        CommandRow row = CommandRow.of(parsed);

        assertNotNull(row, "a captured accessory produced no row, so capture would appear to do nothing");

        assertEquals(row.getKind(), CommandRow.Kind.ACCESSORY);

        assertEquals(row.getTarget(), String.valueOf(parsed.getAddress()),
            "the row points at a different accessory from the captured command");

        // And back again, because the row is what gets saved
        assertEquals(row.toCommand(Accessory.accessoryDecoderType.MM2).toLine(null),
            parsed.toLine(null),
            "a captured command changed on its way through the editor's row");
    }

    /**
     * The new editor's own capture: a line goes in, a row comes out.
     *
     * Needs a display, because the frame is a window.  Skipped rather than failed without one - the
     * two tests above cover the part that can go wrong headlessly.
     */
    @Test
    public void testTheNewEditorAppendsACapturedCommand() throws Exception
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the route editor is a window - this needs a display");
        }

        MarklinAccessory signal = model.newSignal(73, Accessory.accessoryDecoderType.MM2, false);

        signal.setSwitched(true);

        final String captured = signal.toAccessorySettingString();

        final org.traincontrol.gui.RouteEditorFrame[] frame = new org.traincontrol.gui.RouteEditorFrame[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            frame[0] = new org.traincontrol.gui.RouteEditorFrame(null, null);

            frame[0].appendCommand(captured);
        });

        assertEquals(frame[0].commandCount(), 1,
            "a captured command did not reach the command list, so the box would tick and nothing "
            + "would happen");

        javax.swing.SwingUtilities.invokeAndWait(() -> frame[0].dispose());
    }

    /**
     * A command the editor has no controls for keeps its PLACE when the rows around it are edited.
     *
     * The order is the route.  A sub-route call sitting between two turnouts has to still sit between
     * them after the user deletes one of the other rows - and it did not: kept commands were held in a
     * map keyed by the position they had when the route was loaded, while the editable rows moved
     * around underneath them.  Deleting the first row put the sub-route call on the wrong side of the
     * second, silently, having never shown it in the table at all.
     */
    @Test
    public void testAKeptCommandStaysWhereItWasWhenARowIsDeleted() throws Exception
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the route editor is a window - this needs a display");
        }

        final org.traincontrol.gui.RouteEditorFrame[] frame = new org.traincontrol.gui.RouteEditorFrame[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            frame[0] = new org.traincontrol.gui.RouteEditorFrame(null, null);

            // An editable command, one the editor has no controls for, then another editable one
            frame[0].appendCommand(RouteCommand.RouteCommandAccessory(
                1, Accessory.accessoryDecoderType.MM2, true).toLine(null));

            frame[0].appendCommand(RouteCommand.RouteCommandRoute("Yard").toLine(null));

            frame[0].appendCommand(RouteCommand.RouteCommandAccessory(
                5, Accessory.accessoryDecoderType.MM2, true).toLine(null));
        });

        assertEquals(frame[0].commandCount(), 3,
            "a command with no controls must still occupy a row - hiding it is what let it drift");

        List<RouteCommand> before = frame[0].commandsAsSaved();

        assertTrue(before.get(1).isRoute(),
            "the kept command should sit between the two turnouts, where it was put");

        // Delete the FIRST row, which is the ordinary edit that used to move the kept command
        javax.swing.SwingUtilities.invokeAndWait(() -> frame[0].removeCommandAt(0));

        List<RouteCommand> after = frame[0].commandsAsSaved();

        assertEquals(after.size(), 2);

        assertTrue(after.get(0).isRoute(),
            "deleting the row ABOVE a kept command moved that command below the row beneath it - the "
            + "sub-route now runs after the turnout instead of before it, and nothing on screen said "
            + "so");

        assertEquals(after.get(1).getAddress(), 5);

        javax.swing.SwingUtilities.invokeAndWait(() -> frame[0].dispose());
    }

    /**
     * A line that means nothing is ignored rather than ending the capture.
     */
    @Test
    public void testRubbishDoesNotBreakACapture() throws Exception
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the route editor is a window - this needs a display");
        }

        final org.traincontrol.gui.RouteEditorFrame[] frame = new org.traincontrol.gui.RouteEditorFrame[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            frame[0] = new org.traincontrol.gui.RouteEditorFrame(null, null);

            frame[0].appendCommand("this is not a command");
            frame[0].appendCommand(null);
            frame[0].appendCommand("");
        });

        assertEquals(frame[0].commandCount(), 0,
            "rubbish was captured as a command");

        javax.swing.SwingUtilities.invokeAndWait(() -> frame[0].dispose());
    }
}
