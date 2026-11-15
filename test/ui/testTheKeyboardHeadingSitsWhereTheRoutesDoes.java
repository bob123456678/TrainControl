package ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import org.testng.annotations.Test;
import org.traincontrol.gui.AccessoryKeyboard;
import org.traincontrol.gui.TrainControlUI;
import support.Components;
import support.Reflect;

/**
 * Where the keyboard tab's heading ("Control Accessories by Address") and its keys stand, measured on screen.
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

    /** Runs the work with the main window on screen, which hides again after. */
    private static <T> T shown(Callable<T> work) throws Exception
    {
        Components.onTheEventThread(() ->
        {
            ui.setVisible(true);

            return null;
        });

        try
        {
            Components.settle();

            return work.call();
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

    private static JTabbedPane tabs() throws Exception
    {
        JTabbedPane tabs = (JTabbedPane) Reflect.get(TrainControlUI.class, ui, "KeyboardTab");

        assertNotNull(tabs, "the main window has no keyboard tab strip");

        return tabs;
    }

    /**
     * The heading stands where the Routes tab's heading ("Route and Condition Setup") stands, from the tab's own corner
     * (OB-435).
     *
     * MUTATION: give the keyboard heading's GridBag insets in `TrainControlUI.mountKeyboardPanel` another left or top
     * than the Routes tab's, and this fails.
     */
    @Test
    public void testTheTwoHeadingsShareACorner() throws Exception
    {
        JTabbedPane tabs = tabs();

        shown(() ->
        {
            Point keyboard = corner(tabs, "KeyboardPanel", "KeyboardLabel");
            Point routes = corner(tabs, "RoutePanel", "routeLabel");

            assertEquals(keyboard, routes, "the keyboard heading is not where the Routes heading is, from its tab's corner");

            return null;
        });
    }

    /** The screen x of the heading's text, then of each row's first key, with the keyboard tab chosen. */
    private static List<Integer> leftEdges(JTabbedPane tabs) throws Exception
    {
        corner(tabs, "KeyboardPanel", "KeyboardLabel");

        JLabel heading = (JLabel) Reflect.get(TrainControlUI.class, ui, "KeyboardLabel");
        AccessoryKeyboard keys = (AccessoryKeyboard) Reflect.get(TrainControlUI.class, ui, "keyboardKeys");

        return Components.onTheEventThread(() ->
        {
            List<Integer> edges = new ArrayList<>();

            edges.add(heading.getLocationOnScreen().x + heading.getInsets().left);

            for (int row = 0; row < AccessoryKeyboard.ROWS; row++)
            {
                edges.add(keys.number(row * AccessoryKeyboard.COLUMNS + 1).getParent().getLocationOnScreen().x);
            }

            return edges;
        });
    }

    /**
     * Each row's first key starts at the heading's left edge, to the pixel, at the window's size and with the window
     * made wider and taller (MT-954).
     *
     * MUTATION: give the keys' GridBag insets in `TrainControlUI.mountKeyboardPanel` a left of 6, and this fails.
     */
    @Test
    public void testTheKeysStartAtTheHeadingsLeftEdge() throws Exception
    {
        JTabbedPane tabs = tabs();

        shown(() ->
        {
            Dimension was = Components.onTheEventThread(ui::getSize);

            try
            {
                for (int grown : new int[] {0, 300})
                {
                    Components.onTheEventThread(() ->
                    {
                        ui.setSize(was.width + grown, was.height + grown / 3);
                        ui.validate();

                        return null;
                    });

                    Components.settle();

                    List<Integer> edges = leftEdges(tabs);

                    for (int row = 1; row < edges.size(); row++)
                    {
                        assertEquals((int) edges.get(row), (int) edges.get(0), "row " + row + "'s first key does not start at the "
                            + "heading's left edge, with the window " + grown + " pixels wider");
                    }
                }
            }
            finally
            {
                Components.onTheEventThread(() ->
                {
                    ui.setSize(was);

                    return null;
                });
            }

            return null;
        });
    }
}
