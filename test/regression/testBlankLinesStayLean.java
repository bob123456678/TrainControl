package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Blank lines stay lean (FR-230): in `src/`, outside the NetBeans generated code (a line holding `//GEN-BEGIN:`
 * through the one holding `//GEN-END:`, an event handler's `//GEN-FIRST:` and `//GEN-LAST:` lines, and the blank line
 * NetBeans puts before a handler; a handler's body is ours and is scanned), no two blank lines in a row, no blank line right after a line ending in `{`,
 * none right before a line that is only `}`, and the total of blank lines outside generated code (whitespace-only
 * lines) never rises, so NetBeans regenerating a form never fails it.  One blank line between paragraphs of code is the house style and stays.
 */
public class testBlankLinesStayLean
{
    /** Blank lines in `src/` outside generated code, as they stand.  Lower it whenever some go; never raise it. */
    private static final int BLANK_ALLOWED = 30134;

    /** Padding as it stands: lines that follow another blank line, follow a `{` line, or precede a lone `}`. */
    private static final int RUNS_ALLOWED = 0;

    private static final int AFTER_OPEN_ALLOWED = 0;

    private static final int BEFORE_CLOSE_ALLOWED = 0;

    private static final String HOW = ". Take the padding out (one blank line between paragraphs of code stays); never raise the number.";

    /**
     * No blank line follows another one.
     *
     * MUTATION: put two blank lines in a row between two methods of any src file (outside a generated region), and
     * this fails naming the file and line; stop `scan` treating a blank previous line as padding and it fails.
     */
    @Test
    public void testNoTwoBlankLinesInARow() throws Exception
    {
        check(Kind.RUN, RUNS_ALLOWED, "blank lines that follow another blank line");
    }

    /**
     * No blank line follows a line ending in an opening brace.
     *
     * MUTATION: put a blank line after any method's `{` outside a generated region, and this fails naming the file
     * and line; stop `scan` testing the previous line's last character and it fails.
     */
    @Test
    public void testNoBlankLineAfterAnOpeningBrace() throws Exception
    {
        check(Kind.AFTER_OPEN, AFTER_OPEN_ALLOWED, "blank lines right after a line ending in {");
    }

    /**
     * No blank line precedes a line that is only a closing brace.
     *
     * MUTATION: put a blank line above any lone `}` outside a generated region, and this fails naming the file and
     * line; stop `scan` looking past the blank run to the next line and it fails.
     */
    @Test
    public void testNoBlankLineBeforeAClosingBrace() throws Exception
    {
        check(Kind.BEFORE_CLOSE, BEFORE_CLOSE_ALLOWED, "blank lines right before a line that is only }");
    }

    /**
     * The blank lines of `src/` only go down.
     *
     * MUTATION: add one blank line anywhere in src (between two statements will do), and this fails naming the total
     * and the pin; one inside a generated region is not counted.
     */
    @Test
    public void testBlankLinesOnlyGoDown() throws Exception
    {
        int total = 0;

        for (Path file : sources())
        {
            String[] text = lines(file).toArray(new String[0]);

            boolean[] generated = generated(text);

            for (int i = 0; i < text.length; i++) if (!generated[i] && text[i].trim().isEmpty()) total++;
        }

        assertTrue(total <= BLANK_ALLOWED, "src has " + total + " blank lines, the pin is " + BLANK_ALLOWED
            + ": take the new blank lines out (one between paragraphs of code is enough); when blank lines went, lower "
            + "BLANK_ALLOWED in testBlankLinesStayLean to " + total + ".  Never raise it.");
    }

    /**
     * The scan itself, on a fixture: padding is found outside a generated region and left alone inside one.
     *
     * MUTATION: drop the generated-region test in `scan` and this fails.
     */
    @Test
    public void testTheScanSkipsGeneratedRegions()
    {
        String[] fixture = {"class A", "{", "", "    int x;", "", "", "    int y;", "", "}"};

        String[] generated = {"    // <editor-fold> //GEN-BEGIN:initComponents", "    void f() {", "", "", "", "",
            "    }//GEN-END:initComponents", ""};

        for (Kind kind : Kind.values())
        {
            assertEquals(scan(fixture, kind).size(), 1, kind + " in the fixture");
            assertEquals(scan(generated, kind).size(), 0, kind + " in a generated region");
        }
    }

    /**
     * A NetBeans event handler's `//GEN-FIRST:` and `//GEN-LAST:` lines and the blank line NetBeans puts before it are
     * generated, so regenerating a form's handlers never fails the scan or the total; the body between is ours and is
     * scanned (F311R2-B1).
     *
     * MUTATION: drop the `//GEN-FIRST:` test for the blank line before a handler, or treat the body as generated, and
     * this fails.
     */
    @Test
    public void testTheScanSkipsEventHandlers()
    {
        String[] handler = {"class A", "{", "    int x;", "", "",
            "    private void hit(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hit", "", "", "        go();", "",
            "    }//GEN-LAST:event_hit", "}"};

        assertEquals(scan(handler, Kind.RUN), java.util.Arrays.asList(8), "only the run inside the handler's body");

        boolean[] generated = generated(handler);

        assertEquals(generated[3], false, "the blank line after code, two above the handler, is ours");
        assertEquals(generated[4], true, "the blank line right before the handler is NetBeans's");
        assertEquals(generated[6] || generated[7] || generated[9], false, "the handler's body is ours");
    }

    private enum Kind { RUN, AFTER_OPEN, BEFORE_CLOSE }

    private void check(Kind kind, int allowed, String what) throws Exception
    {
        List<String> found = new ArrayList<>();

        for (Path file : sources())
        {
            for (int line : scan(lines(file).toArray(new String[0]), kind)) found.add(file + ":" + line);
        }

        assertTrue(found.size() <= allowed, "src has " + found.size() + " " + what + ", allowed " + allowed + ": "
            + found.subList(0, Math.min(10, found.size())) + " ..." + HOW);
    }

    /** The 1-based lines of `text` that are padding of this kind, outside generated regions. */
    private static List<Integer> scan(String[] text, Kind kind)
    {
        List<Integer> found = new ArrayList<>();

        boolean[] generated = generated(text);

        for (int i = 0; i < text.length; i++)
        {
            if (generated[i] || !text[i].trim().isEmpty()) continue;

            boolean hit;

            if (kind == Kind.RUN)
            {
                hit = i > 0 && text[i - 1].trim().isEmpty();
            }
            else if (kind == Kind.AFTER_OPEN)
            {
                hit = i > 0 && text[i - 1].trim().endsWith("{");
            }
            else
            {
                int next = i + 1;

                while (next < text.length && !generated[next] && text[next].trim().isEmpty()) next++;

                hit = next < text.length && !generated[next] && text[next].trim().equals("}");
            }

            if (hit) found.add(i + 1);
        }

        return found;
    }

    /** NetBeans generated lines: `//GEN-BEGIN:` through `//GEN-END:`, and a handler's first and last lines with the blank line before it. */
    private static boolean[] generated(String[] text)
    {
        boolean[] generated = new boolean[text.length];

        boolean inside = false;

        for (int i = 0; i < text.length; i++)
        {
            if (text[i].contains("//GEN-BEGIN:")) inside = true;

            generated[i] = inside || text[i].contains("//GEN-FIRST:") || text[i].contains("//GEN-LAST:")
                || text[i].trim().isEmpty() && i + 1 < text.length && text[i + 1].contains("//GEN-FIRST:");

            if (text[i].contains("//GEN-END:")) inside = false;
        }

        return generated;
    }

    private static List<Path> sources() throws Exception
    {
        try (Stream<Path> walk = Files.walk(new File("src").toPath()))
        {
            List<Path> files = new ArrayList<>();

            walk.filter(p -> p.toString().endsWith(".java")).sorted().forEach(files::add);

            assertFalse(files.isEmpty(), "no .java file under src: run from the project folder");

            return files;
        }
    }

    private static List<String> lines(Path file) throws Exception
    {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }
}
