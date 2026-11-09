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
     * Each of the five tabs carries its own drawn icon, beside the title it keeps.
     *
     * MUTATION: drop the icon from any `tabs.addTab` in `LocomotiveSettingsFrame.build`, or give two tabs the same
     * icon, and this fails.
     */
    @Test
    public void testEachTabCarriesItsOwnIcon() throws Exception
    {
        JTabbedPane tabs = opened();
        java.util.Set<String> seen = new java.util.HashSet<>();

        assertEquals(tabs.getTabCount(), 5);

        for (int i = 0; i < 5; i++)
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
     * The Autonomy tab's icon is the main window's sidebar Autonomy picture at the tab icon size: its silhouette agrees
     * with the sidebar's, scaled to the same height, on nine pixels in ten.
     *
     * MUTATION: have `TabIcons.autonomy` fill nothing, or draw the old signal, and this fails.
     */
    @Test
    public void testTheAutonomyTabShowsTheSidebarPicture() throws Exception
    {
        JTabbedPane tabs = opened();
        Icon tab = Components.onTheEventThread(() -> tabs.getIconAt(LocomotiveSettingsFrame.Tab.AUTONOMY.ordinal()));
        int w = tab.getIconWidth();
        int h = tab.getIconHeight();

        BufferedImage sidebar = new BufferedImage(TrainControlUI.TAB_ICON_AUTONOMY.getIconWidth(),
            TrainControlUI.TAB_ICON_AUTONOMY.getIconHeight(), BufferedImage.TYPE_INT_ARGB);

        TrainControlUI.TAB_ICON_AUTONOMY.paintIcon(null, sidebar.getGraphics(), 0, 0);

        BufferedImage expected = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = expected.createGraphics();

        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(sidebar, 0, 0, w, h, null);
        g.dispose();

        BufferedImage drawn = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        tab.paintIcon(null, drawn.getGraphics(), 0, 0);

        int same = 0;

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                if ((expected.getRGB(x, y) >>> 24 > 127) == (drawn.getRGB(x, y) >>> 24 > 127)) same++;
            }
        }

        assertTrue(same * 10 >= w * h * 9, "the tab's icon is not the sidebar's picture: " + same + " of " + (w * h)
            + " pixels agree");
    }

    /**
     * The undo arrow beside the preset buttons is a ring open at the upper left, with its head at 12 o'clock pointing
     * straight left: ring at 175 degrees and not at 158, the head's tip drawn left of 12 o'clock on the ring's line.
     *
     * MUTATION: begin the ring in `RowIcons.revert` at 150 degrees, or draw the old head at 11 o'clock, and this fails.
     */
    @Test
    public void testTheUndoArrowIsARingOpenAtTheUpperLeft() throws Exception
    {
        int size = 90;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        org.traincontrol.gui.widgets.RowIcons.revert(size, java.awt.Color.BLACK).paintIcon(null, img.getGraphics(), 0, 0);

        double c = size / 2.0;
        double r = size * 0.30;

        assertTrue(opaque(img, c, r, 175), "the ring does not run round to 175 degrees");
        assertTrue(!opaque(img, c, r, 158), "the ring runs on past 168 degrees: the gap is too small");
        assertTrue(opaque(img, c, r, 270), "the ring is missing at 6 o'clock");
        assertTrue(img.getRGB((int) (c - size * 0.15), (int) (c - r)) >>> 24 > 127, "no head left of 12 o'clock");
        assertTrue(img.getRGB((int) (c - size * 0.10), (int) (c - r - size * 0.13)) >>> 24 <= 127,
            "the head is not square to the ring: it fans out above its tip");
    }

    private static boolean opaque(BufferedImage img, double c, double r, double degrees)
    {
        double a = Math.toRadians(degrees);

        return img.getRGB((int) Math.round(c + r * Math.cos(a)), (int) Math.round(c - r * Math.sin(a))) >>> 24 > 127;
    }

    /**
     * The Functions tab's buttons carry no "Preset" or "Icons" label in front of them: the copy button starts at the
     * table's left edge, and only (i) marks share their row.
     *
     * MUTATION: put a label back in front of Copy Icons in `LocomotiveFunctionsTab`, and this fails.
     */
    @Test
    public void testTheFunctionsTabHasNoPresetOrIconsLabels() throws Exception
    {
        opened();

        java.awt.Container pane = SettingsWindow.showing().getContentPane();
        javax.swing.JButton copy = SettingsWindow.part("functions.copyIcons", javax.swing.JButton.class);
        javax.swing.JTable table = SettingsWindow.part("functions.table", javax.swing.JTable.class);

        for (JLabel l : Components.all(copy.getParent().getParent(), JLabel.class, null))
        {
            assertTrue(l.getIcon() != null && l.getIcon() == InfoMark.icon(), "a label \"" + l.getText()
                + "\" stands in the button row");
        }

        int buttons = Components.onTheEventThread(() -> javax.swing.SwingUtilities.convertPoint(copy.getParent(), copy.getX(), 0,
            pane).x);
        int edge = Components.onTheEventThread(() -> javax.swing.SwingUtilities.convertPoint(table.getParent(), table.getX(), 0,
            pane).x);

        assertTrue(Math.abs(buttons - edge) <= 1, "the buttons start at " + buttons + " and the table at " + edge);
    }

    /**
     * The Functions tab has one row of buttons: Copy Icons and its revert on the left, Save Current Functions as Preset
     * and its revert on the right, the row ending where the table does; and the table starts where the Notes tab's
     * labels do.
     *
     * MUTATION: put the preset buttons back on a row of their own above the copy ones in
     * `LocomotiveFunctionsTab.buttons`, or take the extra inset off `functionsTab` in `LocomotiveSettingsFrame.build`,
     * and this fails.
     */
    @Test
    public void testTheFunctionsButtonsAreOneRowWithThePresetOnTheRight() throws Exception
    {
        opened();
        SettingsWindow.selectTab(LocomotiveSettingsFrame.Tab.FUNCTIONS.ordinal());
        Components.settle();

        java.awt.Container pane = SettingsWindow.showing().getContentPane();
        javax.swing.JButton copy = SettingsWindow.part("functions.copyIcons", javax.swing.JButton.class);
        javax.swing.JButton reset = SettingsWindow.part("functions.resetAll", javax.swing.JButton.class);
        javax.swing.JButton preset = SettingsWindow.part("functions.useCurrent", javax.swing.JButton.class);
        javax.swing.JButton clear = SettingsWindow.part("functions.clearPreset", javax.swing.JButton.class);
        javax.swing.JTable table = SettingsWindow.part("functions.table", javax.swing.JTable.class);

        java.awt.Rectangle c = Components.onTheEventThread(() -> bounds(copy, pane));
        java.awt.Rectangle r = Components.onTheEventThread(() -> bounds(reset, pane));
        java.awt.Rectangle p = Components.onTheEventThread(() -> bounds(preset, pane));
        java.awt.Rectangle k = Components.onTheEventThread(() -> bounds(clear, pane));
        java.awt.Rectangle t = Components.onTheEventThread(() -> bounds(javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, table), pane));

        for (java.awt.Rectangle each : new java.awt.Rectangle[] {r, p, k})
        {
            assertTrue(Math.abs(c.getCenterY() - each.getCenterY()) <= 2, "the buttons are not in one row: " + c + " " + each);
        }

        assertTrue(c.x + c.width <= r.x, "Copy Icons is not left of its revert");
        assertTrue(r.x + r.width < p.x, "the preset buttons are not right of the copy ones");
        assertTrue(p.x + p.width <= k.x, "Clear Preset is not right of Save Current Functions");

        java.awt.Container presetRow = preset.getParent();
        java.awt.Rectangle presetGroup = Components.onTheEventThread(() -> bounds(presetRow, pane));

        assertTrue(Math.abs(presetGroup.x + presetGroup.width - (t.x + t.width)) <= 1, "the preset buttons end at "
            + (presetGroup.x + presetGroup.width) + " and the table at " + (t.x + t.width));

        SettingsWindow.selectTab(LocomotiveSettingsFrame.Tab.NOTES.ordinal());
        Components.settle();

        javax.swing.JLabel startYear = Components.find(pane, JLabel.class,
            l -> UiStyle.withoutColon(I18n.t("loc.ui.labelStartYear")).equals(l.getText()));

        assertNotNull(startYear, "precondition: no Start Year label on Notes");

        int labels = Components.onTheEventThread(() -> bounds(startYear, pane).x);

        assertEquals(t.x, labels, "the Functions table does not start where the Notes labels do");
    }

    /**
     * The Multi-Unit tab starts where the Notes tab does (filter row and list box on the Notes labels' left edge), and
     * each box-column heading starts where its check box's box is painted.
     *
     * MUTATION: put `MultiUnitPanel`'s `gbc.insets` or `scrollGbc.insets` back to 5 on the left, or drop the border
     * from its name or Reverse heading, and this fails.
     */
    @Test
    public void testTheMultiUnitTabAlignsWithTheOthers() throws Exception
    {
        fixture.layDcc(NAME, 3805, LocomotiveSettingsFrame.Tab.NOTES);
        Components.settle();

        java.awt.Container pane = SettingsWindow.showing().getContentPane();
        JLabel startYear = Components.find(pane, JLabel.class,
            l -> UiStyle.withoutColon(I18n.t("loc.ui.labelStartYear")).equals(l.getText()));
        int notes = Components.onTheEventThread(() -> bounds(startYear, pane).x);

        SettingsWindow.selectTab(LocomotiveSettingsFrame.Tab.MULTI_UNIT.ordinal());
        Components.settle();

        JLabel filter = Components.find(pane, JLabel.class,
            l -> UiStyle.withoutColon(I18n.t("loc.ui.filterList")).equals(l.getText()));
        javax.swing.JScrollPane list = (javax.swing.JScrollPane) Components.find(pane, javax.swing.JScrollPane.class,
            s -> s.isShowing() && s.getViewport().getView() instanceof javax.swing.JPanel);
        javax.swing.JCheckBox box = Components.find(pane, javax.swing.JCheckBox.class,
            c -> c.isShowing() && !c.getText().isEmpty());
        JLabel name = Components.find(pane, JLabel.class,
            l -> UiStyle.withoutColon(I18n.t("loc.ui.labelLocomotiveName")).equals(l.getText()) && l.isShowing());

        JLabel reverse = Components.find(pane, JLabel.class,
            l -> UiStyle.withoutColon(I18n.t("loc.ui.labelReverse")).equals(l.getText()) && l.isShowing());
        javax.swing.JCheckBox reverseBox = Components.find(pane, javax.swing.JCheckBox.class,
            c -> c.isShowing() && c.getText().isEmpty());

        assertNotNull(box, "precondition: no member row on the Multi-Unit tab");
        assertNotNull(reverseBox, "precondition: no Reverse box on the Multi-Unit tab");

        int filterX = Components.onTheEventThread(() -> bounds(filter, pane).x);
        int listX = Components.onTheEventThread(() -> bounds(list, pane).x);
        int headingX = Components.onTheEventThread(() -> bounds(name, pane).x + name.getInsets().left);
        int boxX = Components.onTheEventThread(() -> bounds(box, pane).x + box.getInsets().left);
        int reverseX = Components.onTheEventThread(() -> bounds(reverse, pane).x + reverse.getInsets().left);
        int reverseBoxX = Components.onTheEventThread(() -> bounds(reverseBox, pane).x + reverseBox.getInsets().left);

        assertEquals(filterX, notes, "the filter row does not start where the Notes labels do");
        assertEquals(listX, notes, "the list does not start where the Notes labels do");
        assertEquals(headingX, boxX, "the Locomotive Name heading does not start where the check boxes are drawn");
        assertEquals(reverseX, reverseBoxX, "the Reverse heading does not start where its check boxes are drawn");
    }

    /**
     * The (i) on the Functions tab's preset buttons sits to the left of Save Current Functions as Preset, a Save-button
     * distance from it, as the Save button's own (i) does.
     *
     * MUTATION: put the mark last in the `presetGroup` in `LocomotiveFunctionsTab.buttons`, and this fails.
     */
    @Test
    public void testThePresetMarkIsLeftOfItsButton() throws Exception
    {
        opened();
        SettingsWindow.selectTab(LocomotiveSettingsFrame.Tab.FUNCTIONS.ordinal());
        Components.settle();

        java.awt.Container pane = SettingsWindow.showing().getContentPane();
        javax.swing.JButton preset = SettingsWindow.part("functions.useCurrent", javax.swing.JButton.class);
        javax.swing.JButton save = SettingsWindow.save();
        JLabel mark = Components.find(preset.getParent(), JLabel.class, l -> l.getIcon() == InfoMark.icon());
        JLabel saveMark = Components.find(save.getParent(), JLabel.class, l -> l.getIcon() == InfoMark.icon());

        assertNotNull(mark, "precondition: no (i) beside the preset buttons");

        int gap = Components.onTheEventThread(() -> bounds(preset, pane).x - (bounds(mark, pane).x + mark.getWidth()));
        int saveGap = Components.onTheEventThread(() -> bounds(save, pane).x - (bounds(saveMark, pane).x + saveMark.getWidth()));

        assertTrue(Components.onTheEventThread(() -> bounds(mark, pane).x < bounds(preset, pane).x), "the (i) is not left of Save Current Functions");
        assertEquals(gap, saveGap, "the (i) is not the Save button's distance from its control");
    }

    /**
     * Dragged as narrow as the window lets it go, the Functions tab's two button groups still do not overlap, in each
     * language.
     *
     * MUTATION: drop `keepTheFunctionsRowApart` from `LocomotiveSettingsFrame.fit`, and this fails.
     */
    @Test
    public void testTheFunctionsRowNeverOverlapsAtTheNarrowestWindow() throws Exception
    {
        try
        {
            for (String lang : new String[] {"en", "de", "pl", "fr", "es"})
            {
                I18n.setLocale(new java.util.Locale(lang));

                opened();
                SettingsWindow.selectTab(LocomotiveSettingsFrame.Tab.FUNCTIONS.ordinal());
                Components.settle();

                LocomotiveSettingsFrame window = SettingsWindow.showing();

                // A drag to 400 pixels, as far as a minimum the window has set lets it go (an unset one stops nothing)
                Components.onTheEventThread(() ->
                {
                    window.setSize(window.isMinimumSizeSet() ? Math.max(400, window.getMinimumSize().width) : 400, window.getHeight());
                    window.validate();

                    return null;
                });
                Components.settle();

                java.awt.Container pane = window.getContentPane();
                java.awt.Rectangle copy = Components.onTheEventThread(
                    () -> bounds(SettingsWindow.part("functions.copyGroup", javax.swing.JPanel.class), pane));
                java.awt.Rectangle preset = Components.onTheEventThread(
                    () -> bounds(SettingsWindow.part("functions.presetGroup", javax.swing.JPanel.class), pane));

                assertTrue(copy.x + copy.width <= preset.x, lang + ": at " + pane.getWidth() + " wide the preset buttons start at " + preset.x
                    + " and the copy ones end at " + (copy.x + copy.width));
                assertTrue(preset.x + preset.width <= pane.getWidth(), lang + ": the preset buttons run past the window");
            }
        }
        finally
        {
            I18n.setLocale(java.util.Locale.ENGLISH);
        }
    }

    private static java.awt.Rectangle bounds(java.awt.Component c, java.awt.Container pane)
    {
        return javax.swing.SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), pane);
    }

    /**
     * Blue is for section headings: exactly "Name and Address", "Customize Behavior" and "Locomotive Icon" on the first
     * tab, "Notes" over the Notes tab's text box, and the Autonomy tab's two are dark blue, and every item label there
     * and on the other tabs, and the Functions table's column headings, are black, all Semibold 13.
     *
     * MUTATION: make `LocomotiveSettingsFrame.label` build a blue `UiStyle.heading`, or `UiStyle.style` blue, or the
     * Functions table header's foreground blue, or drop `styleHeading(notesCaption)` from
     * `LocomotiveNotesPanel.styleCaptions`, and this fails.
     */
    @Test
    public void testOnlyTheSectionHeadingsAreBlue() throws Exception
    {
        opened();

        String[] blue = { "loc.ui.settingsNameAndAddress", "loc.ui.settingsCustomizeBehavior",
            "loc.ui.settingsLocomotiveIcon", "loc.ui.labelNotes", "loc.ui.settingsAutonomyHeading",
            "loc.ui.settingsAutonomyFunctionsHeading" };
        String[] black = { "ui.name", "loc.ui.decoderType", "layout.ui.address", "loc.ui.settingsPresetSpeed",
            "loc.ui.labelStartYear",
            "loc.ui.labelEndYear", "loc.ui.labelRailwayName", "loc.ui.filterList",
            "autolayout.ui.trainLength", "autolayout.ui.reversible", "autosetup.ui.trainActive",
            "autolayout.ui.departureFunction", "autolayout.ui.arrivalFunction" };

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

        assertEquals(blues.size(), blue.length, "the blue labels are not just the six headings");
    }

    /** Where a named part of the window is, in the window's own coordinates. */
    private static java.awt.Rectangle at(String name, Class<? extends java.awt.Component> type)
    {
        return inWindow(SettingsWindow.part(name, type));
    }

    /** Where the first tab's divider is. */
    private static java.awt.Rectangle divider()
    {
        return inWindow(Components.find(SettingsWindow.showing().getContentPane(), javax.swing.JSeparator.class, null));
    }

    private static java.awt.Rectangle inWindow(java.awt.Component c)
    {
        return Components.onTheEventThread(() -> javax.swing.SwingUtilities.convertRectangle(c.getParent(), c.getBounds(),
            SettingsWindow.showing().getContentPane()));
    }

    /**
     * The divider on the first tab sits midway between the Address row above it and the Customize Behavior heading
     * below it, within 2 pixels.
     *
     * MUTATION: give the divider's row in `LocomotiveSettingsFrame.LocomotiveTab` a bottom inset of 20, and this fails.
     */
    @Test
    public void testTheDividerIsMidwayBetweenTheAddressRowAndTheNextHeading() throws Exception
    {
        opened();

        java.awt.Rectangle can = at("settings.address", javax.swing.JTextField.class);
        java.awt.Rectangle heading = inWindow(labelFor("loc.ui.settingsCustomizeBehavior"));
        java.awt.Rectangle line = divider();

        int above = line.y - (can.y + can.height);
        int below = heading.y - (line.y + line.height);

        assertTrue(Math.abs(above - below) <= 2, "the divider is " + above + " px under Address and " + below
            + " px above Customize Behavior");
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

        int field = at("settings.name", javax.swing.JTextField.class).height;

        assertEquals(inWindow(speed).height, field, "the spinner is not as tall as the fields");
    }

    /**
     * The first tab keeps its fields at the left edge and its picture, at least 260 by 130 and wider than tall, at the
     * right, level with Name and ending where Cancel ends, under a blue "Locomotive Icon" heading that starts at the
     * picture's left edge; "Name and Address" heads the fields and "Customize Behavior" heads Preset Speed, each at the
     * left edge of the labels under it; the trash can is at the tab's lower left, under the divider and Customize Behavior,
     * level with the bottom of the tab.
     *
     * MUTATION: put the picture back in the first column of `LocomotiveSettingsFrame.LocomotiveTab`, or the trash can
     * back under Address, or drop the label, and this fails.
     */
    @Test
    public void testThePictureIsAtTheRightAndTheFieldsAndTheTrashCanAtTheLeft() throws Exception
    {
        opened();

        JLabel picture = SettingsWindow.part("settings.picture", JLabel.class);
        javax.swing.JButton trash = SettingsWindow.part("settings.trash", javax.swing.JButton.class);
        java.awt.Rectangle box = inWindow(picture);
        java.awt.Rectangle can = inWindow(trash);
        java.awt.Rectangle name = at("settings.name", javax.swing.JTextField.class);
        java.awt.Rectangle address = at("settings.address", javax.swing.JTextField.class);
        java.awt.Rectangle nameLabel = inWindow(labelFor("ui.name"));
        java.awt.Rectangle cancel = inWindow(SettingsWindow.cancel());
        java.awt.Rectangle line = divider();
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
        assertTrue(Math.abs(inWindow(caption).x - box.x) <= 1, "the label starts at " + inWindow(caption).x + ", the picture at "
            + box.x);

        java.awt.Rectangle nameHeading = inWindow(labelFor("loc.ui.settingsNameAndAddress"));
        java.awt.Rectangle behaviorHeading = inWindow(labelFor("loc.ui.settingsCustomizeBehavior"));
        java.awt.Rectangle presetLabel = inWindow(labelFor("loc.ui.settingsPresetSpeed"));

        assertTrue(nameHeading.y + nameHeading.height <= name.y, "Name and Address is not above the fields");
        assertEquals(nameHeading.x, nameLabel.x, "Name and Address is not at the labels' left edge");
        assertEquals(behaviorHeading.x, presetLabel.x, "Customize Behavior is not at its label's left edge");
        assertTrue(behaviorHeading.y + behaviorHeading.height <= presetLabel.y, "Customize Behavior is not above Preset Speed");

        java.awt.Rectangle tab = inWindow(trash.getParent());
        int content = tab.y + tab.height - Components.onTheEventThread(() -> trash.getParent().getInsets().bottom);

        assertTrue(address.y + address.height <= line.y && line.y + line.height <= behaviorHeading.y
            && behaviorHeading.y + behaviorHeading.height <= can.y,
            "Address, the divider, Customize Behavior and the trash can are not in that order");
        assertTrue(Math.abs(can.x - nameLabel.x) <= 8, "the trash can (x " + can.x + ") is not at the fields' left edge (x "
            + nameLabel.x + ")");
        assertTrue(content - (can.y + can.height) <= 8, "the trash can ends " + (content - can.y - can.height)
            + " px above the tab's content area");
        assertTrue(can.y + can.height <= cancel.y, "the trash can is not above the Save and Cancel row");
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

    /**
     * Every (i) is light gray (#CCCCCC) with a white "i" and clear corners, so it does not draw the eye (Adam).
     *
     * MUTATION: return the untinted image from `InfoMark.load`, and the disk is dark and this fails.
     */
    @Test
    public void testTheInfoMarkIsLightGray()
    {
        javax.swing.ImageIcon icon = InfoMark.icon();

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        icon.paintIcon(null, image.getGraphics(), 0, 0);

        int[] all = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());

        long gray = java.util.Arrays.stream(all).filter(p -> p >>> 24 != 0 && (p & 0xffffff) == 0xCCCCCC).count();
        long white = java.util.Arrays.stream(all).filter(p -> p >>> 24 != 0 && (p & 0xffffff) == 0xFFFFFF).count();
        long dark = java.util.Arrays.stream(all).filter(p -> p >>> 24 != 0).count() - gray - white;

        assertEquals(dark, 0, "pixels of the mark are neither the gray disk nor the white i");
        assertTrue(gray > 60, "the disk is not #CCCCCC: " + gray + " pixels");
        assertTrue(white > 5, "the i is not white: " + white + " pixels");
        assertEquals(image.getRGB(0, 0) >>> 24, 0, "the corner is not clear");
    }
}
