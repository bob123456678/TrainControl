package support;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The tracker's store, `docs/manual-tests/triage.db`, read through `triagedb.py`: there is no SQLite driver on the
 * classpath, so a test asks python for the text it needs, as `python` on the path, from the repository root.  Each
 * question is asked once per JVM: the store does not change under a test run.
 */
public final class Store
{
    private static final Map<String, String> ANSWERS = new HashMap<>();

    private Store()
    {
    }

    /**
     * The review-finding catalogue as `triagedb.py findings` prints it: `ref<TAB>document<TAB>status<TAB>disposition
     * <TAB>title` per row, `#` for a comment, and below the `# DEAD` line each dead citation as its ref and four dashes.
     */
    public static synchronized String findings() throws IOException
    {
        return ask("findings");
    }

    /** The store's records of these kinds (tests, comments, issues, receipts, ...) as `triagedb.py text` prints them. */
    public static synchronized String text(String... kinds) throws IOException
    {
        List<String> args = new ArrayList<>(Arrays.asList("text"));

        args.addAll(Arrays.asList(kinds));

        return ask(args.toArray(new String[0]));
    }

    /** What `triagedb.py` prints for these arguments; fails when it exits non-zero. */
    private static String ask(String... args) throws IOException
    {
        String key = String.join(" ", args);

        String known = ANSWERS.get(key);

        if (known != null) return known;

        File out = File.createTempFile("triagedb", ".txt");

        try
        {
            List<String> command = new ArrayList<>(Arrays.asList("python", "docs/manual-tests/triagedb.py"));

            command.addAll(Arrays.asList(args));

            ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(out).redirectErrorStream(true);

            builder.environment().put("PYTHONIOENCODING", "utf-8");

            Process process = builder.start();

            process.getOutputStream().close();

            try
            {
                if (!process.waitFor(180, TimeUnit.SECONDS))
                {
                    process.destroyForcibly();

                    throw new AssertionError("triagedb.py " + key + " did not finish in three minutes");
                }
            }
            catch (InterruptedException stopped)
            {
                Thread.currentThread().interrupt();

                throw new IOException("interrupted while triagedb.py " + key + " ran", stopped);
            }

            String text = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");

            if (process.exitValue() != 0) throw new AssertionError("triagedb.py " + key + " failed:\n" + text);

            ANSWERS.put(key, text);

            return text;
        }
        finally
        {
            out.delete();
        }
    }
}
