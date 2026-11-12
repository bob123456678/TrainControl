package ui;

import java.awt.Component;
import java.awt.Point;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import support.Components;
import support.Reflect;

/**
 * The heading of the keyboard tab ("Control Accessories by Address") stands where the Routes tab's heading ("Route
 * and Condition Setup") stands, measured from the tab's own corner on screen (OB-435).
 *
 * MUTATION: give the keyboard heading's GridBag insets in `TrainControlUI.mountKeyboardPanel` another left or top
 * than the Routes tab's, and this fails.
 */
public class testTheKeyboardHeadingSitsWhereTheRoutesDoes extends support.SettingsClass
{
    @Override
    protected String[] made()
    {
        return new String[0];
    }

    /** Where a heading's top left corner is, from the corner of the tab strip's page it is on. */
    private static Point corner(JTabbedPane tabs, String tab, String label) throws Exception
    {
        Component page = (Component) Reflect.get(TrainControlUI.class, ui, tab);
        Component heading = (Component) Reflect.get(TrainControlUI.class, ui, label);

        return Components.onTheEventThread(() ->
        {
            tabs.setSelectedComponent(page);
            tabs.validate();
            tabs.doLayout();

            return SwingUtilities.convertPoint(heading, 0, 0, page);
        });
    }

    @Test
    public void testTheTwoHeadingsShareACorner() throws Exception
    {
        final JTabbedPane tabs = (JTabbedPane) Reflect.get(TrainControlUI.class, ui, "KeyboardTab");

        assertNotNull(tabs, "the main window has no keyboard tab strip");

        Components.onTheEventThread(() ->
        {
            ui.setVisible(true);

            return null;
        });

        try
        {
            Components.settle();

            Point keyboard = corner(tabs, "KeyboardPanel", "KeyboardLabel");
            Point routes = corner(tabs, "RoutePanel", "routeLabel");

            assertEquals(keyboard, routes, "the keyboard heading is not where the Routes heading is, from its tab's corner");
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                ui.setVisible(false);

                return null;
            });
        }
    }
}
