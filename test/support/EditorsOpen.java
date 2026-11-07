package support;

import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;

/** The layout editor windows that are open, for the tests that open and watch them. */
public final class EditorsOpen
{
    private EditorsOpen()
    {
    }

    /** Every editor window that is still there. */
    public static List<LayoutEditor> editors()
    {
        List<LayoutEditor> found = new ArrayList<>();

        for (Frame frame : Frame.getFrames())
        {
            if (frame instanceof LayoutEditor && frame.isDisplayable()) found.add((LayoutEditor) frame);
        }

        return found;
    }

    public static List<String> titles()
    {
        List<String> titles = new ArrayList<>();

        for (LayoutEditor editor : editors()) titles.add(editor.getTitle());

        return titles;
    }

    /** The editor that is showing this page, or null. */
    public static LayoutEditor editorOn(String page)
    {
        for (LayoutEditor editor : editors())
        {
            if (editor.isShowing() && (editor.getTitle().startsWith(I18n.f("app.ui.windowLayoutEditorTitle", page))
                || editor.getTitle().startsWith(I18n.f("autosetup.ui.windowTitle", page)))) return editor;
        }

        return null;
    }

    /** Disposes every editor window. */
    public static void closeAll() throws Exception
    {
        for (LayoutEditor editor : editors())
        {
            final LayoutEditor closing = editor;

            SwingUtilities.invokeAndWait(closing::dispose);
        }
    }
}
