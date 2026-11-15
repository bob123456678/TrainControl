package support;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Test data kept out of the test classes, under `test/fixtures/<testClass>/`, read relative to the working directory
 * the runners use.  The bytes are the data: `.gitattributes` keeps the folder LF.  Not a test.
 */
public final class Fixtures
{
    private static final String FOLDER = "test/fixtures/";

    private Fixtures()
    {
    }

    /** The file at `test/fixtures/<path>` as UTF-8 text, or an error naming the file when it is not there. */
    public static String text(String path)
    {
        File file = new File(FOLDER + path);

        if (!file.isFile()) throw new IllegalStateException("missing test fixture " + file.getAbsolutePath());

        try
        {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("cannot read test fixture " + file.getAbsolutePath(), e);
        }
    }
}
