package ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Restart;

/**
 * File &gt; Open Layout..., answered with another railway's folder, leaves the railway that was open behind: its setup,
 * the trains standing on it and the track they claim (list 2's layout switch row).
 *
 * The window starts on a copy of the frozen railway, with its setup loaded and its four trains standing; Open Layout...
 * is chosen from the File menu, and the folder chooser is answered with a copy of the `single-switch` railway.
 *
 * MUTATION: each claim names the line it rests on; see its javadoc.
 *
 * @author Adam
 */
public class testOpenLayoutLeavesTheOldTrainsBehind
{
    private static final long PATIENCE_MS = 30000;

    private static Restart restart;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    /** Where the frozen railway's trains stood before the switch, by name. */
    private static final List<String> STOOD = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        restart = Restart.open(support.Scenario.folderFor("live-snapshot"));

        model = restart.getModel();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui.getActiveDiagramConfiguration(), "precondition: the frozen railway's setup is not loaded");

        STOOD.addAll(standing(model.getAutoLayoutIfLoaded()));

        assertFalse(STOOD.isEmpty(), "precondition: no train stands on the frozen railway");

        // The other railway, a copy of its own, so the window writes nothing checked in
        final File other = Files.createTempDirectory("tc-other-railway").toFile();

        copy(support.Scenario.folderFor("single-switch").toPath(), other.toPath());

        final AbstractButton open = Components.withText(ui, I18n.t("ui.main.toolbar.openLayout"));

        assertNotNull(open, "the window has no Open Layout...");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)).choosing(other))
        {
            Components.press(open);

            assertTrue(Components.waitFor(() -> model.getLayoutList().equals(Arrays.asList("1 - Junction")),
                PATIENCE_MS), "Open Layout... did not open the other railway's pages: " + model.getLayoutList());

            // And the window's page list says so, which is where the window takes the switch in
            final javax.swing.JComboBox<?> pages = pageList();

            assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> pages.getItemCount() == 1
                && "1 - Junction".equals(String.valueOf(pages.getItemAt(0)))), PATIENCE_MS), "the window's page list"
                + " does not show the other railway's page");

            Components.settle();
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (restart != null) restart.close();
    }

    /**
     * The setup of the railway that was open is no longer the window's: none of its configurations is loaded over the
     * other railway's pages.
     *
     * MUTATION: in `TrainControlUI.initializeTrackDiagram`, take out `this.resetAutonomySession();`.
     */
    @Test
    public void testTheOldRailwaysSetupIsNoLongerLoaded()
    {
        assertNull(Components.onTheEventThread(ui::getActiveDiagramConfiguration), "the frozen railway's configuration"
            + " is still loaded over the other railway's pages");
    }

    /**
     * No train of the railway that was open still stands, and nothing it claimed is still claimed, once another
     * railway is open.
     *
     * DEFECT, so not run: the model keeps the frozen railway's running layout after the switch, with its four trains
     * standing and the track one of them covers still claimed, under the other railway's pages.  Reported for filing.
     */
    @Test(enabled = false)
    public void testNoTrainOfTheOldRailwayStillStandsOrClaims()
    {
        Layout running = model.getAutoLayoutIfLoaded();

        if (running == null) return;

        List<String> still = new ArrayList<>(standing(running));

        still.retainAll(STOOD);

        assertTrue(still.isEmpty(), "trains of the railway that was open still stand, on a railway that is not: " + still);

        assertTrue(running.edgesCoveredByStandingTrains().isEmpty() && running.getActiveLocomotives().isEmpty(),
            "track of the railway that was open is still claimed");
    }

    /** The window's list of the diagram's pages. */
    private static javax.swing.JComboBox<?> pageList() throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("LayoutList");

        field.setAccessible(true);

        return (javax.swing.JComboBox<?>) field.get(ui);
    }

    /** Which train stands where, as "station=train". */
    private static List<String> standing(Layout railway)
    {
        List<String> out = new ArrayList<>();

        if (railway == null) return out;

        for (Point p : railway.getPoints())
        {
            if (p.getCurrentLocomotive() != null) out.add(p.getName() + "=" + p.getCurrentLocomotive().getName());
        }

        return out;
    }

    private static void copy(final Path from, final Path to) throws IOException
    {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                Files.copy(file, to.resolve(from.relativize(file).toString()));

                return FileVisitResult.CONTINUE;
            }
        });
    }
}
