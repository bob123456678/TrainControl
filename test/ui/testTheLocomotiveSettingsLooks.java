package ui;

import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.ToolTipManager;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.InfoMark;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Components;
import support.SettingsWindow;

/** What the Locomotive Settings window looks like: its tab icons, its label style, and the (i) marks. */
public class testTheLocomotiveSettingsLooks
{
    private static final String NAME = "LK train";

    private static support.SettingsFixture fixture;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        fixture = support.SettingsFixture.open();
        model = fixture.model;
        ui = fixture.ui;
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (fixture != null) fixture.finish(NAME);
        }
        finally
        {
            if (fixture != null) fixture.closeSandbox();
        }
    }

    private static JTabbedPane opened() throws Exception
    {
        fixture.quiet();
        fixture.forget(NAME);

        SettingsWindow.power(ui, model, false);

        org.traincontrol.marklin.MarklinLocomotive loc = model.newDCCLocomotive(NAME, 3801);

        SettingsWindow.showOnKeyA(ui, loc);

        assertTrue(SettingsWindow.open(ui, loc, LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null));

        LocomotiveSettingsFrame w = SettingsWindow.awaited();

        assertNotNull(w);

        return w.tabsForTest();
    }

    private static String pixels(Icon icon)
    {
        BufferedImage img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);

        icon.paintIcon(null, img.getGraphics(), 0, 0);

        StringBuilder out = new StringBuilder();

        for (int y = 0; y < img.getHeight(); y++)
        {
            for (int x = 0; x < img.getWidth(); x++) out.append(img.getRGB(x, y) >>> 24 == 0 ? '.' : '#');
        }

        return out.toString();
    }

    /**
     * Each of the four tabs carries its own drawn icon, beside the title it keeps.
     *
     * MUTATION: drop the icon from any `tabs.addTab` in `LocomotiveSettingsFrame.build`, or give two tabs the same
     * icon, and this fails.
     */
    @Test
    public void testEachTabCarriesItsOwnIcon() throws Exception
    {
        JTabbedPane tabs = opened();
        java.util.Set<String> seen = new java.util.HashSet<>();

        assertEquals(tabs.getTabCount(), 4);

        for (int i = 0; i < 4; i++)
        {
            final int tab = i;
            Icon icon = Components.onTheEventThread(() -> tabs.getIconAt(tab));

            assertNotNull(icon, "tab " + i + " has no icon");
            assertTrue(icon.getIconWidth() >= 20, "tab " + i + "'s icon is only " + icon.getIconWidth() + " wide");

            String drawn = pixels(icon);

            assertTrue(drawn.indexOf('#') >= 0, "tab " + i + "'s icon paints nothing");
            assertTrue(seen.add(drawn), "tab " + i + " has the icon of an earlier tab");
            assertTrue(!Components.onTheEventThread(() -> tabs.getTitleAt(tab)).isEmpty(), "tab " + i + " lost its title");
        }
    }

    /**
     * The window's field labels are in the owner's dark blue.
     *
     * MUTATION: build `LocomotiveSettingsFrame.label` as a plain JLabel and this fails.
     */
    @Test
    public void testFieldLabelsAreDarkBlue() throws Exception
    {
        opened();

        JLabel name = Components.find(SettingsWindow.showing().getContentPane(), JLabel.class,
            l -> I18n.t("ui.name").equals(l.getText()));

        assertNotNull(name, "no Name label");
        assertEquals(name.getForeground(), UiStyle.BLUE);
        assertEquals(name.getFont(), UiStyle.LABEL_FONT);
    }

    /**
     * An (i) mark carries the shared icon and its sentence as a tooltip, which stays up long enough to read while the
     * pointer is on it.
     *
     * MUTATION: give `InfoMark.label` no tooltip, or drop the dismiss delay, and this fails.
     */
    @Test
    public void testTheInfoMarkShowsASentenceLongEnoughToRead()
    {
        JLabel mark = InfoMark.label("A sentence");

        assertEquals(mark.getIcon(), InfoMark.icon());
        assertTrue(mark.getToolTipText().contains("A sentence"));

        int was = ToolTipManager.sharedInstance().getDismissDelay();

        for (java.awt.event.MouseListener l : mark.getMouseListeners())
        {
            l.mouseEntered(new java.awt.event.MouseEvent(mark, java.awt.event.MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false));
        }

        assertTrue(ToolTipManager.sharedInstance().getDismissDelay() >= 20000, "the tooltip would vanish in "
            + ToolTipManager.sharedInstance().getDismissDelay() + " ms");

        for (java.awt.event.MouseListener l : mark.getMouseListeners())
        {
            l.mouseExited(new java.awt.event.MouseEvent(mark, java.awt.event.MouseEvent.MOUSE_EXITED, 0, 0, 1, 1, 0, false));
        }

        assertEquals(ToolTipManager.sharedInstance().getDismissDelay(), was);
    }
}
