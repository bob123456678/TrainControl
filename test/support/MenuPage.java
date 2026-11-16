package support;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;

/**
 * A diagram session over pages of its own in a temporary folder, for the claims that compare the editor's right-click
 * menu with its keys; closing deletes the folder.  Skips when there is no display, as the editor panel builds Swing.
 *
 * @author Adam
 */
public final class MenuPage implements AutoCloseable
{
    public final AutonomySession session;

    private final File folder;

    /** Opens one page named "main": a run of track and a label. */
    public MenuPage(String tag) throws IOException
    {
        this(tag, Diagrams.aPageWithARunAndALabel("main"));
    }

    /** Opens a page of that shape for each name. */
    public MenuPage(String tag, String first, String second) throws IOException
    {
        this(tag, Diagrams.aPageWithARunAndALabel(first), Diagrams.aPageWithARunAndALabel(second));
    }

    public MenuPage(String tag, LayoutDiagram... pages) throws IOException
    {
        Display.needed("AutonomyEditorPanel builds real Swing components - this needs a display");

        folder = Files.createTempDirectory(tag).toFile();

        try
        {
            session = new AutonomySession(folder);

            session.open(java.util.Arrays.asList(pages));
        }
        catch (IOException | RuntimeException e)
        {
            Trees.delete(folder);

            throw e;
        }
    }

    /** Makes the square at 1,1 of "main" a station and returns it. */
    public TileKey aStation() throws IOException
    {
        session.initialize("CtrlB");

        TileKey station = new TileKey("main", 1, 1);

        session.setStation(station, true);
        session.rebuild();

        return station;
    }

    @Override
    public void close()
    {
        Trees.delete(folder);
    }

    /** The labels of every item on a menu, as far as it is a menu (null has none). */
    public static List<String> itemNames(JPopupMenu menu)
    {
        List<String> out = new ArrayList<>();

        if (menu == null) return out;

        for (java.awt.Component each : menu.getComponents())
        {
            if (each instanceof JMenuItem) out.add(((JMenuItem) each).getText());
        }

        return out;
    }
}
