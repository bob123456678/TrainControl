package ui;

import java.awt.image.BufferedImage;
import javax.swing.JDialog;
import org.testng.annotations.Test;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.locomotive.UsageChart;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;
import support.Components;
import support.Looks;
import support.SettingsWindow;

/**
 * FR-177: the hand-built windows that the Locomotive Settings look claims do not reach wear the application's fonts and
 * colors: blue for section headings, black Semibold 13 for item and column labels, Bold 12 for buttons.
 */
public class testTheHandBuiltWindowsWearTheStandard extends support.SettingsClass
{
    private static final String NAME = "FR177 train";

    @Override
    protected String[] made()
    {
        return new String[] { NAME };
    }

    /**
     * The Multi-Unit tab's column headings are item labels, black Semibold 13 like every other tab's.
     *
     * MUTATION: build `MultiUnitPanel`'s heading labels with `new JLabel` and a Bold 12 font, as they were, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheMultiUnitColumnHeadingsAreItemLabels() throws Exception
    {
        fixture.layDcc(NAME, 3811, LocomotiveSettingsFrame.Tab.MULTI_UNIT);

        java.awt.Container pane = SettingsWindow.showing().getContentPane();

        for (String key : new String[] { "loc.ui.labelLocomotiveName", "loc.ui.labelSpeedMultiplierPercent",
            "loc.ui.labelReverse", "loc.ui.filterList" })
        {
            Looks.assertLabel(pane, UiStyle.withoutColon(I18n.t(key)), UiStyle.TEXT, "the Multi-Unit tab");
        }

        SettingsWindow.showing().dispose();
    }

    /**
     * The usage chart's buttons, range segments and figures follow the standard.
     *
     * MUTATION: leave `UsageChart.pagingButton` or `SegmentedControl.Segment` in the default font, or make the summary
     * line bold by `deriveFont`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheUsageChartWearsTheStandard() throws Exception
    {
        UsageChart chart = Components.onTheEventThread(() -> new UsageChart(ui));

        try
        {
            Looks.assertButtonsAreBold12(chart.getContentPane(), "the usage chart");

            for (String name : new String[] { "usageChart.summary", "usageChart.firstDay", "usageChart.lastDay" })
            {
                javax.swing.JLabel l = Components.named(chart.getContentPane(), name, javax.swing.JLabel.class);

                org.testng.Assert.assertEquals(l.getFont(), UiStyle.LABEL_FONT, name + " is not Semibold 13");
                org.testng.Assert.assertEquals(l.getForeground(), UiStyle.TEXT, name + " is not black");
            }
        }
        finally
        {
            Components.onTheEventThread(() -> { chart.dispose(); return null; });
        }
    }

    /**
     * The icon crop dialog's Zoom label is an item label and its three buttons are Bold 12.
     *
     * MUTATION: build the dialog's Reset, OK or Cancel button, or its Zoom label, with the default font, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheCropDialogWearsTheStandard() throws Exception
    {
        JDialog dialog = Components.onTheEventThread(() ->
        {
            java.lang.reflect.Constructor<?> c = Class.forName("org.traincontrol.gui.locomotive.LocIconCropDialog")
                .getDeclaredConstructor(java.awt.Window.class, String.class, BufferedImage.class, int.class, int.class);

            c.setAccessible(true);

            return (JDialog) c.newInstance(ui, "Crop", new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB), 48, 24);
        });

        try
        {
            Looks.assertButtonsAreBold12(dialog.getContentPane(), "the crop dialog");
            Looks.assertLabel(dialog.getContentPane(), I18n.t("loc.ui.cropZoom"), UiStyle.TEXT, "the crop dialog");
        }
        finally
        {
            Components.onTheEventThread(() -> { dialog.dispose(); return null; });
        }
    }
}
