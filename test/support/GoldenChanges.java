package support;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertEquals;

/**
 * A golden with the changes a branch makes on purpose, listed line by line: each row of the list is a golden line
 * number, the line as the golden has it and the line now ("(gone)" when there is none, a backslash and n for a line
 * break), tab-separated.  A row whose
 * line no longer matches the golden fails, so a changed golden cannot pass unnoticed.
 *
 * @author Adam
 */
public final class GoldenChanges
{
    private GoldenChanges()
    {
    }

    /** The golden's text with the rows of `changes` applied; the golden as it is when there is no such file. */
    public static String apply(String golden, String changes) throws Exception
    {
        if (!new File(changes).isFile())
        {
            return golden;
        }

        List<String> lines = new ArrayList<>(java.util.Arrays.asList(golden.split("\n", -1)));

        for (String change : Files.readAllLines(new File(changes).toPath(), StandardCharsets.UTF_8))
        {
            String[] f = change.split("\t");
            assertEquals(f.length, 3, changes + " row is not line, was and now: " + change);

            int at = Integer.parseInt(f[0]) - 1;
            assertEquals(lines.get(at), f[1], changes + " names golden line " + f[0] + " as it was");
            // A literal backslash and n in the new line is a line break, so a row can add a line
            lines.set(at, f[2].equals("(gone)") ? null : f[2].replace("\\n", "\n"));
        }

        lines.removeIf(java.util.Objects::isNull);
        return String.join("\n", lines);
    }
}
