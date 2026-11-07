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
     * The first tab's labels (Name, Decoder Type, Address, Preset Speed, Locomotive Icon) are dark blue and the other tabs' item labels
     * and the Functions table's column headings are black, all Semibold 13: nothing else in the window is blue.
     *
     * MUTATION: make `LocomotiveSettingsFrame.label` black, or `UiStyle.style` blue, or the Functions table header's
     * foreground blue, and this fails.
     */
    @Test
    public void testBasicSettingsLabelsAreBlueAndTheOthersBlack() throws Exception
    {
        opened();

        String[] blue = { "ui.name", "loc.ui.decoderType", "layout.ui.address", "loc.ui.settingsPresetSpeed",
            "loc.ui.settingsLocomotiveIcon" };
        String[] black = { "loc.ui.settingsPresetLabel", "loc.ui.settingsIconsLabel", "loc.ui.labelStartYear",
            "loc.ui.labelEndYear", "loc.ui.labelRailwayName", "loc.ui.labelNotes", "loc.ui.filterList" };

        for (String key : blue)
        {
            JLabel l = labelFor(key);

            assertNotNull(l, "no label for " + key);
            assertEquals(l.getForeground(), UiStyle.BLUE, key + " is not blue");
            assertEquals(l.getFont(), UiStyle.LABEL_FONT, key + " is not Semibold 13");
        }

        for (String key : black)
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

        java.util.List<JLabel> blues = Components.all(SettingsWindow.showing().getContentPane(), JLabel.class,
            l -> UiStyle.BLUE.equals(l.getForeground()));

        assertEquals(blues.size(), blue.length, "blue labels are only the first tab's five");
    }

    private static java.awt.Rectangle inWindow(java.awt.Component c)
    {
        return Components.onTheEventThread(() -> javax.swing.SwingUtilities.convertRectangle(c.getParent(), c.getBounds(),
            SettingsWindow.showing().getContentPane()));
    }

    /**
     * The divider on the first tab sits midway between the trash can above it and the Preset Speed spinner below it,
     * within 2 pixels.
     *
     * MUTATION: give the divider's row in `LocomotiveSettingsFrame.LocomotiveTab` a bottom inset of 20, and this fails.
     */
    @Test
    public void testTheDividerIsMidwayBetweenTheTrashCanAndPresetSpeed() throws Exception
    {
        opened();

        java.awt.Rectangle can = inWindow(SettingsWindow.part("settings.trash", javax.swing.JButton.class));
        java.awt.Rectangle speed = inWindow(SettingsWindow.part("settings.speed", javax.swing.JSpinner.class));
        java.awt.Rectangle line = inWindow(Components.find(SettingsWindow.showing().getContentPane(),
            javax.swing.JSeparator.class, null));

        int above = line.y - (can.y + can.height);
        int below = speed.y - (line.y + line.height);

        assertTrue(Math.abs(above - below) <= 2, "the divider is " + above + " px under the trash can and " + below
            + " px above Preset Speed");
    }

    /**
     * The Preset Speed label, its spinner, the % sign and the (i) share one vertical center, and the spinner is as tall
     * as the fields above.
     *
     * MUTATION: add 10 to the spinner's preferred height in `LocomotiveSettingsFrame.LocomotiveTab`, or set the % label's
     * alignment so it sits on the top of the row, and this fails.
     */
    @Test
    public void testThePresetSpeedRowIsCenteredAndAsTallAsTheFields() throws Exception
    {
        opened();

        JLabel label = labelFor("loc.ui.settingsPresetSpeed");
        javax.swing.JSpinner speed = SettingsWindow.part("settings.speed", javax.swing.JSpinner.class);
        java.awt.Container row = speed.getParent();
        JLabel percent = Components.find(row, JLabel.class, l -> "%".equals(l.getText()));
        JLabel info = Components.find(row, JLabel.class, l -> l != percent && l.getIcon() != null || l != percent && l.getText() != null && l.getText().length() > 1);

        assertNotNull(percent, "no % sign");
        assertNotNull(info, "no (i) mark");

        int center = inWindow(speed).y + inWindow(speed).height / 2;

        for (java.awt.Component c : new java.awt.Component[] { label, percent, info })
        {
            java.awt.Rectangle r = inWindow(c);

            assertTrue(Math.abs(r.y + r.height / 2 - center) <= 1, c + " is not on the spinner's center line");
        }

        int field = inWindow(SettingsWindow.part("settings.name", javax.swing.JTextField.class)).height;

        assertEquals(inWindow(speed).height, field, "the spinner is not as tall as the fields");
    }

    /**
     * The first tab keeps its fields at the left edge and its picture, at least 260 by 130 and wider than tall, at the
     * right, level with Name and ending where Cancel ends, under a blue "Locomotive Icon" label; the trash can is under
     * Address, at the fields' left edge, above the divider and Preset Speed.
     *
     * MUTATION: put the picture back in the first column of `LocomotiveSettingsFrame.LocomotiveTab`, or the trash can
     * back under the picture, or drop the label, and this fails.
     */
    @Test
    public void testThePictureIsAtTheRightAndTheFieldsAndTheTrashCanAtTheLeft() throws Exception
    {
        opened();

        JLabel picture = SettingsWindow.part("settings.picture", JLabel.class);
        javax.swing.JButton trash = SettingsWindow.part("settings.trash", javax.swing.JButton.class);
        java.awt.Rectangle box = inWindow(picture);
        java.awt.Rectangle can = inWindow(trash);
        java.awt.Rectangle name = inWindow(SettingsWindow.part("settings.name", javax.swing.JTextField.class));
        java.awt.Rectangle address = inWindow(SettingsWindow.part("settings.address", javax.swing.JTextField.class));
        java.awt.Rectangle speed = inWindow(SettingsWindow.part("settings.speed", javax.swing.JSpinner.class));
        java.awt.Rectangle nameLabel = inWindow(labelFor("ui.name"));
        java.awt.Rectangle cancel = inWindow(SettingsWindow.cancel());
        java.awt.Rectangle line = inWindow(Components.find(SettingsWindow.showing().getContentPane(),
            javax.swing.JSeparator.class, null));
        JLabel caption = labelFor("loc.ui.settingsLocomotiveIcon");

        assertTrue(box.width >= 260 && box.height >= 130, "the picture box is only " + box.width + " by " + box.height);
        assertTrue(box.width > box.height, "the picture box is not wide like a locomotive picture");
        assertTrue(Math.abs(box.x + box.width - (cancel.x + cancel.width)) <= 1, "the picture ends at " + (box.x + box.width)
            + ", Cancel at " + (cancel.x + cancel.width));
        assertTrue(name.x + name.width < box.x, "the fields are not left of the picture");
        assertTrue(nameLabel.x <= 20, "the labels start " + nameLabel.x + " px in");
        assertTrue(Math.abs(Math.min(name.y, box.y) - box.y) <= 12 && name.y - box.y <= 12,
            "Name (y " + name.y + ") is not level with the picture (y " + box.y + ")");

        assertNotNull(caption, "no Locomotive Icon label");
        assertEquals(caption.getForeground(), UiStyle.BLUE, "the Locomotive Icon label is not blue");
        assertTrue(inWindow(caption).y + inWindow(caption).height <= box.y, "the label is not above the picture");

        assertTrue(can.y >= address.y + address.height, "the trash can is not under Address");
        assertTrue(Math.abs(can.x - nameLabel.x) <= 8, "the trash can (x " + can.x + ") is not at the fields' left edge (x "
            + nameLabel.x + ")");
        assertTrue(can.y + can.height <= line.y && line.y + line.height <= speed.y,
            "the trash can, the divider and Preset Speed are not in that order");
    }

    /**
     * With no picture the box shows the application's stand-in locomotive at the box's width, not stretched.
     *
     * MUTATION: show a blank 10 by 10 image instead of `LocomotivePlaceholder.image` for a locomotive without a picture in
     * `LocomotiveSettingsFrame.LocomotiveTab.refreshPicture`, and this fails.
     */
    @Test
    public void testNoPictureShowsTheStandInLocomotive() throws Exception
    {
        opened();

        JLabel picture = SettingsWindow.part("settings.picture", JLabel.class);
        Icon shown = Components.onTheEventThread(picture::getIcon);

        assertNotNull(shown, "the empty picture box shows nothing");

        int box = Components.onTheEventThread(picture::getWidth);

        assertTrue(shown.getIconWidth() <= box - 2 && shown.getIconHeight() <= picture.getHeight() - 2, "the stand-in does not fit the box");
        assertEquals(pixels(shown), pixels(new javax.swing.ImageIcon(
            org.traincontrol.gui.locomotive.LocomotivePlaceholder.image(shown.getIconWidth()))),
            "the box does not show the application's stand-in locomotive");
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
