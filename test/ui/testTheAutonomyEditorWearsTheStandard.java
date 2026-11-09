package ui;

import javax.swing.JDialog;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.util.I18n;
import support.Components;
import support.Looks;
import support.Reflect;

/** FR-177: the autonomy editor and its signal dialog wear the standard's fonts and colors. */
public class testTheAutonomyEditorWearsTheStandard
{
    private static support.FrozenEditor frozen;

    @BeforeClass
    public static void open() throws Exception
    {
        frozen = support.FrozenEditor.open();
    }

    @AfterClass(alwaysRun = true)
    public static void close() throws Exception
    {
        if (frozen != null) frozen.close();
    }

    /**
     * Test a path, Why not Moving, Pick Stations, Save and Cancel are Bold 12, and the "Path Type" heading is the
     * heading blue, not a darker one.
     *
     * MUTATION: set `AutonomyEditorPanel.FONT_BUTTON` back to Bold 11, drop the `UiStyle.button` calls on the editor's
     * Save and Cancel, or give the Path Type heading a different blue, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsButtonsAndHeadingsWearTheStandard() throws Exception
    {
        LayoutEditor editor = frozen.editorOn("1 - Main");

        try
        {
            Looks.assertButtonsAreBold12(editor.getContentPane(), "the autonomy editor");
            Looks.assertLabel(editor.getContentPane(), I18n.t("autosetup.ui.labelPathType"), UiStyle.BLUE,
                "the autonomy editor");
        }
        finally
        {
            support.FrozenEditor.dispose(editor);
        }
    }

    /**
     * The guard signal dialog's four buttons are Bold 12.
     *
     * MUTATION: build any of `EditorSignals.askAboutSignals`'s buttons with `new JButton` alone, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheSignalDialogsButtonsWearTheStandard() throws Exception
    {
        LayoutEditor editor = frozen.editorOn("1 - Main");

        try
        {
            AutonomyEditorPanel panel = Components.onTheEventThread(() -> editor.getAutonomyPanel());
            Class<?> kind = null;

            for (Class<?> inner : AutonomyEditorPanel.class.getDeclaredClasses())
            {
                if (inner.isEnum() && inner.getSimpleName().equals("Guard")) kind = inner;
            }

            final Object exit = kind.getEnumConstants()[0];
            final TileKey station = Components.onTheEventThread(() -> frozen.session.getReducer().getPoints().keySet()
                .stream().filter(k -> frozen.session.getStore().isStation(k)).findFirst().get());
            final java.lang.reflect.Method ask =
                Reflect.method(AutonomyEditorPanel.class, "askAboutSignals", kind, TileKey.class);

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                try { ask.invoke(panel, exit, station); } catch (Exception e) { throw new RuntimeException(e); }
            });

            final String title = I18n.t("autosetup.ui.menuPairSignal");
            final JDialog[] found = new JDialog[1];

            Components.waitFor(() ->
            {
                found[0] = Components.onTheEventThread(() ->
                {
                    for (java.awt.Window w : java.awt.Window.getWindows())
                    {
                        if (w instanceof JDialog && w.isShowing() && title.equals(((JDialog) w).getTitle())) return (JDialog) w;
                    }

                    return null;
                });

                return found[0] != null;
            }, 10000);

            JDialog dialog = found[0];

            org.testng.Assert.assertNotNull(dialog, "the signal dialog did not open");

            Looks.assertButtonsAreBold12(dialog.getContentPane(), "the signal dialog");

            final JDialog open = dialog;

            Components.onTheEventThread(() -> { open.dispose(); return null; });
        }
        finally
        {
            support.FrozenEditor.dispose(editor);
        }
    }
}
