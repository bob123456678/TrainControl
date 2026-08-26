import java.awt.Component;
import java.io.File;
import java.io.PrintWriter;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Where every label on a diagram actually lands, as a text file that can be diffed.
 *
 * OB-115 and FR-028's centring are both questions about POSITION, and position is the one thing that
 * cannot be established by reading a layout manager. This builds the real grid, at several tile sizes,
 * over several pages, and writes out the bounds of every component - so "did anything else move" is a
 * diff rather than an opinion.
 *
 * **It waits for the tile images first**, and the first version did not. A tile's preferred size
 * depends on whether its icon has arrived, the icons decode on a pool, and so the same build measured
 * twice disagreed about forty tiles - which would have been read as a change caused by whatever was
 * being tested. The control that found it was running the same build twice, which is the first thing
 * to do with any harness that reports differences.
 *
 * Against a COPY of the layout, with the preference pointed at it and put back afterwards.
 */
public class Bounds
{
    public static void main(String[] args) throws Exception
    {
        final String folder = args[0];
        final String out = args[1];

        java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

        final String was = prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, was)));

        prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, folder);

        MarklinControlStation model = MarklinControlStation.init(null, true, false, false, true);

        final TrainControlUI[] ui = new TrainControlUI[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> ui[0] = new TrainControlUI());

        ui[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        System.out.println("session: " + (ui[0].getAutonomySession() != null));

        for (final int size : new int[] { 30, 60 })
        {
            for (final String name : model.getLayoutList())
            {
                final LayoutDiagram page = model.getLayout(name);

                final JPanel panel = new JPanel();

                final LayoutGrid[] grid = new LayoutGrid[1];

                javax.swing.SwingUtilities.invokeAndWait(() ->
                    grid[0] = new LayoutGrid(page, size, panel, null, true, ui[0]));

                settle(ui[0]);

                final java.awt.Container box = grid[0].getContainer();

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    box.setSize(box.getPreferredSize());
                    box.doLayout();

                    // Twice: a GridBagLayout settles on the second pass when a component's preferred
                    // size depends on a border set after it was added, which is exactly this case.
                    box.doLayout();
                });

                File file = new File(out,
                    name.replaceAll("[^A-Za-z0-9]", "_") + "-" + size + ".txt");

                try (PrintWriter writer = new PrintWriter(file, "UTF-8"))
                {
                    writer.println("page " + name + " at " + size + "px, panel "
                        + box.getWidth() + "x" + box.getHeight());

                    java.util.List<String> lines = new java.util.ArrayList<>();

                    for (Component one : box.getComponents())
                    {
                        String text = one instanceof JLabel ? ((JLabel) one).getText() : "";

                        if (text == null) text = "";

                        lines.add(String.format("%-22s %-40s x=%4d y=%4d w=%4d h=%4d",
                            one.getClass().getSimpleName(),
                            text.replace('\n', ' ').replace('\r', ' '),
                            one.getX(), one.getY(), one.getWidth(), one.getHeight()));
                    }

                    java.util.Collections.sort(lines);

                    for (String line : lines) writer.println(line);

                    writer.println("components: " + box.getComponentCount());
                }

                System.out.println("wrote " + file);
            }
        }

        System.exit(0);
    }

    /**
     * Waits until no tile image is still being decoded, and then a little longer.
     *
     * The count is what the diagram's own hold-back waits on, so it is the right question; the extra
     * pass afterwards is because a decode finishing and the label wearing the image are two different
     * moments, and it is the second one that changes a preferred size.
     */
    private static void settle(TrainControlUI ui) throws Exception
    {
        for (int tries = 0; tries < 300 && !ui.tilesAreSettled(); tries++)
        {
            Thread.sleep(50);
        }

        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);

        javax.swing.SwingUtilities.invokeLater(() -> ui.whenTilesSettled(done::countDown));

        done.await(30, java.util.concurrent.TimeUnit.SECONDS);

        // Two empty tasks: the reveal is posted, and whatever it queues is posted behind that.
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }
}
