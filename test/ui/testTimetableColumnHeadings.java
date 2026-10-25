package ui;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;

/**
 * The timetable's column headings, before there is anything to put under them (Adam: "the timetable entry has default
 * table heading when blank.  make sure these are always set.").  The form designer gives every new JTable four columns
 * called "Title 1" through "Title 4", and the real headings were installed only by repaintTimetable, which needs a
 * running Layout, so a fresh installation showed the placeholder.  The headings do not depend on the data, so they are
 * set on their own.
 *
 * MUTATION: take the prepareTimetableColumns call out of the constructor and the table
 * comes back with four columns named after nothing.
 *
 * @author Adam
 */
public class testTimetableColumnHeadings
{
    /** Five columns, named, on a window that has never seen a timetable. */
    @Test
    public void testTheTimetableIsNamedBeforeItHasAnythingInIt() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new org.testng.SkipException("the timetable is a table in a window");
        }

        javax.swing.JTable timetable = timetableOf(build());

        assertEquals(timetable.getColumnCount(), 5,
            "the timetable still has the form designer's four placeholder columns, which is what "
            + "Adam saw on the blank tab");

        String[] expected = {
            I18n.t("timetable.ui.columnIndex"),
            I18n.t("timetable.ui.columnLocomotive"),
            I18n.t("timetable.ui.columnStart"),
            I18n.t("timetable.ui.columnDestination"),
            I18n.t("timetable.ui.columnTime")
        };

        for (int column = 0; column < expected.length; column++)
        {
            assertEquals(timetable.getColumnName(column), expected[column],
                "column " + column + " is not the heading it should be");

            assertFalse(timetable.getColumnName(column).startsWith("Title "),
                "column " + column + " is still the designer's placeholder");
        }

        assertEquals(timetable.getRowCount(), 0,
            "the designer's four blank rows are still in the table, under headings that now describe "
            + "journeys nobody has captured");
    }

    /** The window, with the layout preference pointed somewhere that is not Adam's railway (OB-111). */
    private TrainControlUI build() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] built = new TrainControlUI[1];

        try
        {
            // Inside the try, so nothing between the open and the close can leave the preference behind.
            sandbox = support.LayoutSandbox.open();

            javax.swing.SwingUtilities.invokeAndWait(() -> built[0] = new TrainControlUI());
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }

        return built[0];
    }

    /** The timetable table itself, which is a generated field and so has no accessor. */
    private javax.swing.JTable timetableOf(TrainControlUI ui) throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("timetable");

        field.setAccessible(true);

        return (javax.swing.JTable) field.get(ui);
    }
}
