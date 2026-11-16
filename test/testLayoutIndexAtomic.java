import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.util.Util;

/**
 * Tests that the diagram editor saves the page index (gleisbild.cs2) without ever cutting it off.
 *
 * The index names every page there is.  It used to be opened for writing directly, which empties it at
 * once, so a write that failed part way left a header naming no pages.  Like testAtomicWrite, the
 * failure is provoked with a page list that throws part way through, and what matters is what is left
 * on disk afterwards.
 */
public class testLayoutIndexAtomic
{
    private static final String BEFORE = "[gleisbild]\nthe previous index, which must survive\n";

    private Path dir;
    private File index;

    @BeforeMethod
    public void setUp() throws Exception
    {
        dir = Files.createTempDirectory("tc-index");
        File config = new File(dir.toFile(), "config");
        assertTrue(config.mkdirs());
        index = new File(config, "gleisbild.cs2");

        Files.write(index.toPath(), BEFORE.getBytes(StandardCharsets.UTF_8));
    }

    @AfterMethod
    public void tearDown() throws Exception
    {
        Files.walk(dir)
            .sorted(java.util.Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    private String contents() throws Exception
    {
        return new String(Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * The ordinary case: a completed write replaces the index, with the page names and ids.
     */
    @Test
    public void testACompletedSaveWritesTheIndex() throws Exception
    {
        LayoutDiagram.writeLayoutIndex(dir.toString(), Arrays.asList("Main", "Yard \u00e4"));

        assertEquals(contents(), "[gleisbild]\nversion\n .major=1\ngroesse\nseite\n .name=Main\nseite\n .id=2\n .name=Yard \u00e4\n");
        assertFalse(new File(index.getAbsolutePath() + Util.PARTIAL_DOWNLOAD_SUFFIX).exists(),
            "the staging file must not be left behind");
    }

    /**
     * A save that fails part way leaves the old index intact, and no staging file behind.
     */
    @Test
    public void testAFailedSaveLeavesTheOldIndexIntact() throws Exception
    {
        List<String> pages = new AbstractList<String>()
        {
            @Override
            public String get(int i)
            {
                if (i == 0)
                {
                    return "Main";
                }

                throw new IllegalStateException("interrupted part way");
            }

            @Override
            public int size()
            {
                return 2;
            }
        };

        try
        {
            LayoutDiagram.writeLayoutIndex(dir.toString(), pages);
            fail("the failure must reach the caller");
        }
        catch (IOException | RuntimeException expected)
        {
        }

        assertEquals(contents(), BEFORE, "a failed save destroyed the previous page index");
        assertFalse(new File(index.getAbsolutePath() + Util.PARTIAL_DOWNLOAD_SUFFIX).exists(),
            "the staging file must be cleaned up");
    }
}
