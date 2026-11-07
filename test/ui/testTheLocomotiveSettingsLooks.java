package ui;

import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.ToolTipManager;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
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
public class testTheLocomotiveSettingsLooks extends support.SettingsClass
{
    private static final String NAME = "LK train";


    @Override
    protected String[] made()
    {
        return new String[] { NAME };
    }

    private static JTabbedPane opened() throws Exception
    {
        fixture.layDcc(NAME, 3801, LocomotiveSettingsFrame.Tab.LOCOMOTIVE);

        return SettingsWindow.showing().tabsForTest();
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

    private static JLabel labelFor(String key)
    {
        String text = UiStyle.withoutColon(I18n.t(key));

        return Components.find(SettingsWindow.showing().getContentPane(), JLabel.class, l -> text.equals(l.getText()));
    }

    /**
     * Every item label and the Functions table's column headings are Semibold 13 in black ("blue for section
     * headings, black for item text"), and nothing in this window is dark blue: it has no section headings.
     *
     * MUTATION: make `UiStyle.style` set `UiStyle.BLUE` again, or the Functions table header's foreground, and this
     * fails.
     */
    @Test
    public void testItemLabelsAreBlackAndNothingIsBlue() throws Exception
    {
        opened();

        String[] keys = { "ui.name", "loc.ui.decoderType", "layout.ui.address", "loc.ui.settingsPresetSpeed",
            "loc.ui.settingsPresetLabel", "loc.ui.settingsIconsLabel", "loc.ui.labelStartYear", "loc.ui.labelEndYear",
            "loc.ui.labelRailwayName", "loc.ui.labelNotes", "loc.ui.filterList" };

        for (String key : keys)
        {
            JLabel l = labelFor(key);

            assertNotNull(l, "no label for " + key);
            assertEquals(l.getForeground(), UiStyle.TEXT, key + " is not black");
            assertEquals(l.getFont(), UiStyle.LABEL_FONT, key + " is not Semibold 13");
        }

        javax.swing.table.JTableHeader header = SettingsWindow.part("functions.table", javax.swing.JTable.class)
            .getTableHeader();

        assertEquals(header.getForeground(), UiStyle.TEXT, "the column headings are not black");
        assertEquals(header.getFont(), UiStyle.LABEL_FONT, "the column headings are not Semibold 13");

        java.util.List<JLabel> blue = Components.all(SettingsWindow.showing().getContentPane(), JLabel.class,
            l -> UiStyle.BLUE.equals(l.getForeground()));

        assertTrue(blue.isEmpty(), "dark blue is for section headings and this window has none, yet: "
            + (blue.isEmpty() ? "" : blue.get(0).getText()));
    }

    /**
     * The first tab's picture box is at least 260 by 130, wider than tall like a locomotive picture, with the trash can
     * under it and the fields to its right, level with its top.
     *
     * MUTATION: set `PICTURE_WIDTH` and `PICTURE_HEIGHT` in `LocomotiveSettingsFrame` back to 200 and 90, or move the
     * trash can's row beside the picture, and this fails.
     */
    @Test
    public void testThePictureBoxIsLargeWithTheTrashCanUnderIt() throws Exception
    {
        opened();

        JLabel picture = SettingsWindow.part("settings.picture", JLabel.class);
        javax.swing.JButton trash = SettingsWindow.part("settings.trash", javax.swing.JButton.class);
        javax.swing.JTextField name = SettingsWindow.part("settings.name", javax.swing.JTextField.class);
        java.awt.Container root = SettingsWindow.showing().getContentPane();

        java.awt.Rectangle box = Components.onTheEventThread(
            () -> javax.swing.SwingUtilities.convertRectangle(picture.getParent(), picture.getBounds(), root));
        java.awt.Rectangle can = Components.onTheEventThread(
            () -> javax.swing.SwingUtilities.convertRectangle(trash.getParent(), trash.getBounds(), root));
        java.awt.Rectangle field = Components.onTheEventThread(
            () -> javax.swing.SwingUtilities.convertRectangle(name.getParent(), name.getBounds(), root));

        assertTrue(box.width >= 260 && box.height >= 130, "the picture box is only " + box.width + " by " + box.height);
        assertTrue(box.width > box.height, "the picture box is not wide like a locomotive picture");
        assertTrue(can.y >= box.y + box.height, "the trash can (y " + can.y + ") is not under the picture (bottom "
            + (box.y + box.height) + ")");
        assertTrue(can.x >= box.x && can.x < box.x + box.width, "the trash can is not under the picture's width");
        assertTrue(field.x >= box.x + box.width, "the fields are not to the right of the picture");
        assertTrue(Math.abs(Math.min(field.y, box.y) - box.y) <= 12 && field.y - box.y <= 12,
            "the first field (y " + field.y + ") is not top-aligned with the picture (y " + box.y + ")");
    }

    /**
     * The revert icons beside Preset and Icons are dim when there is nothing to put back, lit when there is a preset or
     * a customization, and the Preset one clears the preset on a click and dims again.
     *
     * MUTATION: leave `refreshReverts` out of `changed`, or enable the icon always, and this fails.
     */
    @Test
    public void testTheRevertIconsLightWhenThereIsSomethingToReset() throws Exception
    {
        org.traincontrol.marklin.MarklinLocomotive loc = fixture.layDcc(NAME, 3802, LocomotiveSettingsFrame.Tab.FUNCTIONS);
        javax.swing.JButton clear = SettingsWindow.part("functions.clearPreset", javax.swing.JButton.class);
        javax.swing.JButton reset = SettingsWindow.part("functions.resetAll", javax.swing.JButton.class);
        javax.swing.JTable table = SettingsWindow.part("functions.table", javax.swing.JTable.class);

        assertTrue(!Components.onTheEventThread(clear::isEnabled), "Clear Preset is lit with no preset");
        assertTrue(!Components.onTheEventThread(reset::isEnabled), "Reset All is lit with nothing customized");

        Components.onTheEventThread(() ->
        {
            table.setValueAt(Boolean.TRUE, 1, 3);

            return null;
        });

        assertTrue(Components.onTheEventThread(clear::isEnabled), "a ticked preset did not light Clear Preset");

        SettingsWindow.press(clear);

        assertEquals(Components.onTheEventThread(() -> table.getValueAt(1, 3)), Boolean.FALSE, "the click left the tick");
        assertTrue(!Components.onTheEventThread(clear::isEnabled), "Clear Preset stayed lit after it cleared");

        loc.setCustomFunctions(true);
        // The window asks again every second
        assertTrue(Components.waitFor(() -> Components.onTheEventThread(reset::isEnabled), 8000),
            "a customized locomotive did not light Reset All");
    }

    /**
     * The Fn column holds the widest function number and its heading, and keeps exactly that width when the window is
     * made wider, the extra room going to the other columns.
     *
     * MUTATION: drop `fixTheFnColumn` from the tab's constructor and the width changes with the window; this fails.
     */
    @Test
    public void testTheFnColumnKeepsItsWidthWhenTheWindowResizes() throws Exception
    {
        org.traincontrol.marklin.MarklinLocomotive loc = fixture.layDcc(NAME, 3803, LocomotiveSettingsFrame.Tab.FUNCTIONS);
        LocomotiveSettingsFrame window = SettingsWindow.showing();
        javax.swing.JTable table = SettingsWindow.part("functions.table", javax.swing.JTable.class);

        int before = Components.onTheEventThread(() -> table.getColumnModel().getColumn(0).getWidth());
        int needs = Components.onTheEventThread(() -> table.getFontMetrics(table.getFont()).stringWidth("F" + (loc.getNumF() - 1)));

        assertTrue(before > needs, "F" + (loc.getNumF() - 1) + " needs " + needs + " pixels and the column is " + before);

        Components.onTheEventThread(() ->
        {
            window.setSize(window.getWidth() + 300, window.getHeight());
            window.validate();

            return null;
        });

        Components.settle();

        assertEquals((int) Components.onTheEventThread(() -> table.getColumnModel().getColumn(0).getWidth()), before,
            "the Fn column changed width with the window");
        assertTrue(!Components.onTheEventThread(() -> table.getColumnModel().getColumn(0).getResizable()), "the user can resize it");
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
