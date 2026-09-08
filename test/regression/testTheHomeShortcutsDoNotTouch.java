package regression;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.AutonomyEditorPanel;

/**
 * OB-188: the two shortcut buttons in the set-home dialog touch each other.
 *
 * Adam: *"add some spacing between the two buttons, as they currently touch (in the set home
 * locomotive popup)."*  The strip that carries "use current" and "use active" was laid out with
 * `new FlowLayout(FlowLayout.LEFT, 0, 0)` - a horizontal gap of zero - so the two buttons were drawn
 * edge to edge and read as one wide control with a line down the middle.
 *
 * Measured rather than read.  The defect is a distance in pixels, so the test lays the strip out and
 * asks where the buttons landed; a test that looked for a number in the source would pass for a gap
 * written into a layout nothing uses.
 *
 * MUTATION: put the horizontal gap in `AutonomyEditorPanel.shortcutRow` back to 0, or stack the
 * buttons instead of spacing them, and this fails.
 *
 * @author Adam
 */
public class testTheHomeShortcutsDoNotTouch
{
    /** The smallest separation that reads as two controls rather than one. */
    private static final int READS_AS_TWO = 4;

    /**
     * Lays a row out at its own preferred size and returns the horizontal space between its buttons.
     *
     * @param row the strip to measure, already holding exactly two buttons
     * @return the pixels between the first button's right edge and the second button's left edge
     */
    private static int gapBetweenTheButtons(javax.swing.JPanel row)
    {
        row.setSize(row.getPreferredSize());
        row.doLayout();

        java.awt.Component first = row.getComponent(0);
        java.awt.Component second = row.getComponent(1);

        assertTrue(first.getWidth() > 0 && second.getWidth() > 0,
            "the buttons were laid out with no width at all, so the distance between them means "
            + "nothing - this measurement is broken, not the dialog");

        assertEquals(first.getY(), second.getY(),
            "the two buttons are no longer on one line.  Spacing them apart vertically is not what "
            + "was asked for, and the dialog is too small to spend a row on");

        return second.getX() - (first.getX() + first.getWidth());
    }

    /**
     * Two buttons on the strip the dialog actually builds are not touching.
     *
     * The control is the second half: the same two buttons in a zero-gap row, which is what the
     * dialog used to build.  Without it, a measurement that always answered "far apart" - one that
     * read the wrong components, or a layout that never ran - would pass this test for a dialog that
     * still looks exactly as Adam described it.
     */
    @Test
    public void testTheTwoShortcutButtonsAreNotDrawnEdgeToEdge()
    {
        javax.swing.JButton parked = new javax.swing.JButton("Use current: BR 218");
        javax.swing.JButton active = new javax.swing.JButton("Use active: V 200");

        javax.swing.JPanel row = AutonomyEditorPanel.shortcutRow();

        row.add(parked);
        row.add(active);

        int gap = gapBetweenTheButtons(row);

        assertTrue(gap >= READS_AS_TWO,
            "the set-home dialog's two shortcut buttons are " + gap + " pixels apart, so they touch "
            + "and read as one control (OB-188)");

        // THE CONTROL.  This is the layout the dialog had, built here so the measurement above is
        // known to be able to see a pair of buttons that touch.
        javax.swing.JPanel touching = new javax.swing.JPanel(
            new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

        touching.add(new javax.swing.JButton("Use current: BR 218"));
        touching.add(new javax.swing.JButton("Use active: V 200"));

        assertEquals(gapBetweenTheButtons(touching), 0,
            "a zero-gap row did not measure as touching, so this test cannot tell the two layouts "
            + "apart and the assertion above proves nothing");
    }

    /**
     * And the dialog is built from that strip.
     *
     * The measurement above is about `shortcutRow`.  If `pickLocomotive` went back to making its own
     * panel, the spacing would be correct in a method nothing calls - which is the shape this project
     * has been caught by before: a rule that is written down and not asked.
     */
    @Test
    public void testThePickerBuildsItsShortcutStripFromThatRow() throws Exception
    {
        String source = new String(Files.readAllBytes(
            Paths.get("src/org/traincontrol/gui/AutonomyEditorPanel.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("JPanel row = shortcutRow();"),
            "pickLocomotive no longer builds its shortcut strip with shortcutRow, so the spacing "
            + "measured above belongs to a panel the dialog does not use (OB-188)");
    }
}
