import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Two things a reader of the source can check whole, found by the 3.0.0 release review and carried to 2.8.2: a file read
 * into text is read in the character set it was written in, and the graph's right-click menu hangs its messages from the
 * main window.
 *
 * Read from the source because both doors sit behind a file chooser and a popup menu, which a test cannot open without
 * a screen; the 3.0 branch drives both doors on a real window (regression.testTheRoutesImportDoorAsksByName,
 * ui.testWhereHisTrainsMayBeSent).
 */
public class testFilesAndMessagesMeetTheirReaders
{
    /** The project folder: the working directory, as NetBeans runs the tests, unless a run names another. */
    private static final File ROOT = new File(System.getProperty("traincontrol.projectRoot", "."));

    /**
     * Every file read into a String names its character set (RLU-A1).
     *
     * Routes > Import read its file as `new String(Files.readAllBytes(...))` - the machine's own character set, which on
     * Java 8 under Windows is a code page - while Routes > Export and the backup write UTF-8.  So "Ausfahrt Sud" with an
     * umlaut came back as other letters, and a route's locomotive command naming a locomotive with an accented name named
     * no locomotive and was skipped when the route fired.
     *
     * MUTATION: read the routes file without a character set, and this fails naming it.
     */
    @Test
    public void testEveryFileReadIntoTextNamesItsCharacterSet() throws Exception
    {
        List<String> bare = new ArrayList<>();
        int reads = 0;

        for (File source : javaSources(new File(ROOT, "src")))
        {
            String text = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);

            Matcher m = Pattern.compile("new String\\(\\s*Files\\.readAllBytes").matcher(text);

            while (m.find())
            {
                reads++;

                int end = text.indexOf(';', m.end());
                String statement = text.substring(m.start(), end < 0 ? text.length() : end);

                if (!statement.contains("Charset")) bare.add(source.getName() + ": " + statement.trim());
            }
        }

        assertTrue(reads > 0, "precondition: no file read into text was found under src/ - run from the project root");

        assertTrue(bare.isEmpty(), "a file is read into text in the machine's own character set, where every door "
            + "writes UTF-8 - on Windows its accented letters come back as others (RLU-A1): " + bare);
    }

    /**
     * No message on the graph's right-click menu is hung from the menu itself (RLU-B2).
     *
     * By the time an item's action runs the menu has left its window, so a message parented on it belongs to Swing's
     * hidden frame - and with Window Always on Top ticked, the default, it opened beneath the main window and held every
     * window with nothing to show why: "power on to start", and every other refusal there.
     *
     * MUTATION: parent one of them on the menu again, and this fails naming it.
     */
    @Test
    public void testTheGraphMenusMessagesBelongToTheMainWindow() throws Exception
    {
        File menu = new File(ROOT, "src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java");

        assertTrue(menu.isFile(), "precondition: the graph's right-click menu is not at " + menu);

        String text = new String(Files.readAllBytes(menu.toPath()), StandardCharsets.UTF_8);

        Matcher m = Pattern.compile("JOptionPane\\.show\\w*\\(\\s*this\\s*,").matcher(text);

        List<String> onTheMenu = new ArrayList<>();

        while (m.find()) onTheMenu.add("line " + (text.substring(0, m.start()).split("\n", -1).length));

        assertTrue(text.contains("JOptionPane"), "precondition: the menu shows no message at all");

        assertTrue(onTheMenu.isEmpty(), "a message on the graph's right-click menu is hung from the menu, which has left "
            + "its window by the time it shows - with Window Always on Top it opens beneath the main window (RLU-B2): "
            + onTheMenu);
    }

    private static List<File> javaSources(File folder)
    {
        List<File> out = new ArrayList<>();

        File[] children = folder.listFiles();

        if (children == null) return out;

        for (File child : children)
        {
            if (child.isDirectory()) out.addAll(javaSources(child));
            else if (child.getName().endsWith(".java")) out.add(child);
        }

        return out;
    }
}
