import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.marklin.file.CS2File;

/**
 * A sensor a route uses survives the layout read, though no page draws it.
 *
 * Reading a layout folder deleted every sensor that no page draws.  A route waits on its sensor's
 * record, and the next frame re-creates the sensor already occupied, so a route triggered by
 * "clear, then occupied" missed that arrival, and a condition read clear under a train.  A sensor
 * that is on no page and in no route still goes.
 */
public class testLayoutRoutePrune
{
    @Test
    public void testASensorARouteUsesSurvivesTheLayoutRead() throws Exception
    {
        MarklinControlStation model = init(null, true, false, false, true);

        File folder = Files.createTempDirectory("tc-prune").toFile();
        File config = new File(folder, "config");
        File pages = new File(config, "gleisbilder");

        assertTrue(pages.mkdirs(), "could not build the fixture");

        write(new File(config, "gleisbild.cs2"),
            "[gleisbild]\nversion\n .major=1\ngroesse\n .wert=0\nseite\n .name=Reads\n");

        // One page, drawing one sensor, 5
        write(new File(pages, "Reads.cs2"),
            "[gleisbildseite]\nversion\n .major=1\nelement\n .id=0x101\n .typ=s88kontakt\n .artikel=5\n");

        String route = "Prune probe route";

        try
        {
            for (int s = 5; s <= 8; s++) model.newFeedback(s, null);

            // Triggered by 6, conditioned on 7; 8 is used by nothing
            List<RouteCommand> condition = Arrays.asList(RouteCommand.RouteCommandFeedback(7, true));

            assertTrue(model.newRoute(route, new LinkedList<>(), 6, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED,
                false, NodeExpression.fromList(condition)), "precondition: the route was not added");

            Field parserField = MarklinControlStation.class.getDeclaredField("fileParser");
            parserField.setAccessible(true);
            ((CS2File) parserField.get(model)).setLayoutDataLoc(
                "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/");

            Method sync = MarklinControlStation.class.getDeclaredMethod("syncLayouts");
            sync.setAccessible(true);
            sync.invoke(model);

            assertTrue(model.isFeedbackSet("5"), "the sensor on the page was deleted: not measuring what it should");
            assertTrue(model.isFeedbackSet("6"),
                "the sensor a route triggers on was deleted because no page draws it");
            assertTrue(model.isFeedbackSet("7"),
                "the sensor a route's condition names was deleted because no page draws it");
            assertFalse(model.isFeedbackSet("8"), "a sensor on no page and in no route survived the read");
        }
        finally
        {
            model.deleteRoute(route);
            model.stop();

            Files.walk(folder.toPath())
                .sorted(java.util.Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(File::delete);
        }
    }

    private static void write(File file, String text) throws java.io.IOException
    {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
