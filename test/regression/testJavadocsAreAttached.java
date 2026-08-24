package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * A javadoc block immediately followed by another javadoc block documents nothing.
 *
 * TD-14, from the three-day history review. In Java only the LAST doc comment before a declaration
 * attaches, so when an insertion lands between a javadoc and the thing it was written for, the older
 * one silently becomes prose in the middle of a file - and the member it described is left with no
 * documentation at all.
 *
 * Nothing warns about it, and it has happened repeatedly here. The three the review names are the ones
 * that matter: `Layout`'s `locomotiveInBlock` parameters sat above `refreshProtectingSignal`, which
 * commands real signals; `AutonomyCompanionStore`'s "a tile that moves leaves its setup behind"
 * rationale sat above `forgetTiles`, leaving `moveTiles` undocumented; and `Util`'s entire explanation
 * of why the locomotive database is staged and moved into place - the best paragraph in that file -
 * sat above `sanitizeFilename`, leaving `writeAtomically`, the primitive standing between the
 * operator's accumulated work and a truncated file, with no javadoc at all. Three more were found and
 * fixed while working through this review.
 *
 * **In a codebase where the comment IS the safety mechanism, a comment attached to the wrong member is
 * the same kind of defect as a guard on the wrong branch.**
 *
 * **A ratchet, not a clean sheet.** There are over a hundred, most of them harmless - two descriptions
 * of the same method stacked above it still describe it accurately. Fixing them all is a day of
 * reading with a real chance of moving a paragraph onto the wrong thing, which is the defect itself.
 * So this pins the number and requires it to go DOWN, never up: a new one fails the build with the
 * file named, and every one that gets fixed can be banked by lowering the cap.
 *
 * @author Adam
 */
public class testJavadocsAreAttached
{
    /**
     * What was there when this test was written. Lower it whenever some are fixed; never raise it.
     */
    private static final int ALLOWED = 98;

    @Test
    public void testNoNewOrphanedJavadocs() throws Exception
    {
        File src = new File("src");

        assertTrue(src.isDirectory(), "cannot find " + src.getAbsolutePath()
            + " - this test reads the source, so it has to run from the project root");

        List<String> files = new ArrayList<>();

        collect(src, files);

        int found = 0;

        List<String> worst = new ArrayList<>();

        for (String path : files)
        {
            int here = orphansIn(new String(
                Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));

            found += here;

            if (here > 0) worst.add(path + " (" + here + ")");
        }

        assertTrue(found <= ALLOWED,
            "there are now " + found + " javadoc blocks that document nothing, up from " + ALLOWED
            + ". A javadoc immediately followed by another javadoc attaches to nothing, so whatever it "
            + "was written for is now undocumented and the text sits in the middle of the file looking "
            + "like it still applies. Files: " + worst);

        // And banked when they go, so the number cannot quietly drift back up to where it was
        assertEquals(found, ALLOWED,
            found + " orphaned javadocs remain, fewer than the " + ALLOWED + " recorded. Lower "
            + "ALLOWED to " + found + " so the improvement is kept.");
    }

    /**
     * Doc blocks with nothing but whitespace between the end of one and the start of the next.
     *
     * Only whitespace: anything else - a field, a method, an annotation - means the first block has a
     * declaration to attach to and is doing its job.
     */
    private int orphansIn(String source)
    {
        int found = 0;

        for (int at = source.indexOf("/**"); at >= 0; at = source.indexOf("/**", at + 3))
        {
            int ends = source.indexOf("*/", at + 3);

            if (ends < 0) break;

            int next = source.indexOf("/**", ends + 2);

            if (next < 0) break;

            if (source.substring(ends + 2, next).trim().isEmpty()) found++;
        }

        return found;
    }

    private void collect(File from, List<String> into)
    {
        File[] children = from.listFiles();

        if (children == null) return;

        for (File child : children)
        {
            if (child.isDirectory()) collect(child, into);
            else if (child.getName().endsWith(".java")) into.add(child.getPath());
        }
    }
}
