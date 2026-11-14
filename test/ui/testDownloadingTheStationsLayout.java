package ui;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuListener;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Reflect;
import support.StandInStation;
import support.StationRig;
import support.Trees;

/**
 * Layouts &gt; Download Central Station Layout Files, pressed in a window started on a stand-in CS3's layout (the pages
 * of `test/cs3_v261`): the copy is then the layout in use, as Open Layout would leave it.
 */
public class testDownloadingTheStationsLayout
{
    private File root;
    private StationRig rig;
    private TrainControlUI ui;
    private String recentWas;
    private String lastLocalWas;

    @BeforeMethod
    public void openOnTheStation() throws Exception
    {
        support.Display.needed("the door is a menu item of the main window");

        recentWas = TrainControlUI.getPrefs().get(TrainControlUI.RECENT_LAYOUTS_PREF, null);
        lastLocalWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_LOCAL_LAYOUT_PREF, null);
        root = Files.createTempDirectory("tc-download-door").toFile();

        BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("260", null);

        rig = new StationRig(root, cs3::apply, true, true);
        ui = (TrainControlUI) rig.model.getGUI();

        assertTrue(Components.waitFor(() -> !rig.model.getLayoutList().isEmpty(), 30000),
            "precondition: the window did not read the station's pages");

        Components.settle();

        assertTrue(ui.isRemoteLayout(), "precondition: the window is not on the station's layout");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception
    {
        try
        {
            support.EditorsOpen.closeAll();

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(closing::dispose);
            }
        }
        finally
        {
            if (rig != null) rig.close();

            putBack(TrainControlUI.RECENT_LAYOUTS_PREF, recentWas);
            putBack(TrainControlUI.LAST_LOCAL_LAYOUT_PREF, lastLocalWas);

            Trees.delete(root);
        }
    }

    private static void putBack(String key, String was)
    {
        if (was == null) TrainControlUI.getPrefs().remove(key);
        else TrainControlUI.getPrefs().put(key, was);
    }

    /** Presses the door, choosing `into` in its folder chooser and answering each question by `rule`. */
    private Answering press(File into, java.util.function.Function<String, Answering.Answer> rule) throws Exception
    {
        Answering answering = Answering.start(rule).choosing(into);

        final JMenuItem door = (JMenuItem) Reflect.get(TrainControlUI.class, ui, "downloadCSLayoutMenuItem");

        SwingUtilities.invokeLater(door::doClick);

        return answering;
    }

    /** The Autonomy menu's items as the user opens it. */
    private List<String> autonomyMenu() throws Exception
    {
        final List<String> items = new ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            JMenuBar bar = ui.getJMenuBar();
            String heading = I18n.t("autosetup.ui.menuAutonomy");

            for (int i = 0; i < bar.getMenuCount(); i++)
            {
                JMenu menu = bar.getMenu(i);

                if (menu == null || !heading.equals(menu.getText())) continue;

                for (MenuListener listener : menu.getMenuListeners()) listener.menuSelected(null);

                for (int at = 0; at < menu.getItemCount(); at++)
                {
                    if (menu.getItem(at) != null) items.add(menu.getItem(at).getText());
                }
            }
        });

        return items;
    }

    /**
     * After the download the Autonomy menu offers autonomy on the copy, not "Autonomy needs a layout stored on this
     * computer", which the window said until a restart; and a folder holding no layout is not asked about.
     *
     * MUTATION: put back the door's own sequence (`syncWithCS2`, `repaintLayout`) in place of `openLayoutFolder`, and the
     * menu still says autonomy needs a layout on this computer.
     */
    @Test(timeOut = 120000)
    public void testAfterTheDownloadAutonomyCanBeSetUpOnTheCopy() throws Exception
    {
        final String needsALocalLayout = I18n.t("autosetup.ui.menuNoSetupPossible");
        final File into = new File(root, "copy");

        assertTrue(into.mkdirs(), "precondition: no folder to download into");
        assertTrue(autonomyMenu().contains(needsALocalLayout),
            "precondition: on the station's layout the Autonomy menu does not say it needs a local layout: "
            + autonomyMenu());

        final String complete = I18n.f("layout.ui.infoDownloadCompleteLayoutSavedIn", into.getAbsolutePath());

        try (Answering answering = press(into, message -> Answering.pressAt(0)))
        {
            assertTrue(Components.waitFor(() -> answering.asked().contains(complete), 60000),
                "the download did not say it was complete: " + answering.asked());

            Components.settle();

            assertTrue(!ui.isRemoteLayout(), "the copy is not the layout in use");
            assertFalse(autonomyMenu().contains(needsALocalLayout),
                "after the download the Autonomy menu still says autonomy needs a layout on this computer: "
                + autonomyMenu());
            assertTrue(answering.asked().size() == 2, "the download asked more than its two notices: "
                + answering.asked());
        }
    }
}
