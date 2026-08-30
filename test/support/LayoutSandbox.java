package support;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.prefs.Preferences;
import org.traincontrol.gui.TrainControlUI;

/**
 * A throwaway copy of the fixture layout, with the window pointed at it.
 *
 * OB-111. A full battery left `cs2_sample_layout` - Adam's real railway - showing as modified in
 * `git status` after every run. The content was unchanged, differing only in line endings, so
 * battery.sh's fingerprint correctly stayed quiet; what it cost is that on 2026-08-25 the churn masked
 * a change he had made himself, and telling the two apart meant reading the JSON semantically.
 *
 * **The cause is not a test that names his folder - none of them do.** Three classes construct the real
 * window, and the window opens whatever the saved layout preference names. On his machine that is his
 * railway. The fixture separation of b87c4f05 moved the suite onto `test/test_layout` and could not reach
 * this, because the path is not written in the suite at all: it is in his preferences.
 *
 * So the preference is what this changes, and it changes it to a COPY rather than to `test/test_layout`
 * itself - the window writes as well as reads, and the fixture is tracked, so pointing it at the real
 * fixture would move the same problem one folder over and put it in the repository.
 *
 * Usage is two lines, in @BeforeClass and @AfterClass:
 *
 *     sandbox = LayoutSandbox.open();      // and construct the window AFTER this
 *     sandbox.close();
 *
 * @author Adam
 */
public final class LayoutSandbox
{
    private final Preferences prefs = TrainControlUI.getPrefs();

    private final String was;

    private final Path folder;

    private LayoutSandbox(Path folder, String was)
    {
        this.folder = folder;
        this.was = was;
    }

    /**
     * Copies the fixture layout somewhere temporary and points the window's layout preference at it.
     *
     * @return the sandbox, to be closed when the class is done with it
     * @throws IOException if the fixture cannot be copied
     */
    public static LayoutSandbox open() throws IOException
    {
        File fixture = new File("test/test_layout");

        Path to = Files.createTempDirectory("tc-sandbox-layout");

        if (fixture.isDirectory()) copy(fixture.toPath(), to);

        Preferences prefs = TrainControlUI.getPrefs();

        String was = prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, to.toFile().getAbsolutePath());

        return new LayoutSandbox(to, was);
    }

    /**
     * Puts the preference back.
     *
     * Back to what it WAS, including back to unset - because a test that leaves a path behind has
     * changed which layout the application opens the next time the operator starts it, which is worse
     * than the churn this class exists to remove.
     */
    public void close()
    {
        if (was == null || was.isEmpty())
        {
            prefs.remove(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF);
        }
        else
        {
            prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, was);
        }
    }

    /**
     * @return where the copy lives, for a test that wants to look at what was written
     */
    public File getFolder()
    {
        return folder.toFile();
    }

    private static void copy(final Path from, final Path to) throws IOException
    {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
            throws IOException
            {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                Files.copy(file, to.resolve(from.relativize(file).toString()),
                    StandardCopyOption.REPLACE_EXISTING);

                return FileVisitResult.CONTINUE;
            }
        });
    }
}
