package support;

import javax.swing.SwingUtilities;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;

/**
 * One train standing alone on a named station of a frozen railway, for claims about what the setup keeps of it, and the
 * doors such a claim reloads and reads the setup through.  Not a test.
 */
public final class StandingTrain
{
    private StandingTrain()
    {
    }

    /** The square the store names so; fails when the railway has none. */
    public static TileKey squareNamed(AutonomySession session, String name)
    {
        for (TileKey key : session.getStore().getNamedTiles())
        {
            if (name.equals(session.getStore().getPointName(key))) return key;
        }

        throw new AssertionError("this railway has no " + name);
    }

    /** Takes every train off the railway and stands this one on the first copy of the square that takes it, in the setup too. */
    public static void standOn(MarklinControlStation model, AutonomySession session, String train, TileKey square)
        throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            Layout railway = model.getAutoLayout();

            railway.getPoints().stream().filter(p -> p.getCurrentLocomotive() != null)
                .forEach(p -> railway.moveLocomotive(null, p.getName(), true));

            session.facingsFor(square).keySet().stream().map(railway::getPoint).filter(p -> p != null).findFirst()
                .ifPresent(p -> railway.moveLocomotive(train, p.getName(), false));

            session.placeLocomotive(square, train);
        });

        if (model.getAutoLayout().getLocomotiveLocation(model.getLocByName(train)) == null)
        {
            throw new AssertionError("precondition: " + train + " could not be stood on " + square);
        }
    }

    /** Loads a configuration without folding the running layout into it first, answering yes to what it asks. */
    public static void reloadWithoutFolding(TrainControlUI ui, String configuration) throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(configuration, false, false));

            Components.settle();
            Components.settle();
        }
    }

    /** Everything the setup keeps on disk under the sandbox, by file. */
    public static java.util.Map<String, String> setupFiles(java.io.File sandbox) throws Exception
    {
        java.util.Map<String, String> out = new java.util.TreeMap<>();

        java.nio.file.Path root = new java.io.File(sandbox, "config" + java.io.File.separator + "autonomy").toPath();

        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root))
        {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) walk.filter(java.nio.file.Files::isRegularFile)::iterator)
            {
                out.put(root.relativize(file).toString(),
                    new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        return out;
    }
}
