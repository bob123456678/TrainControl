import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import org.traincontrol.base.NodeExpression;

/**
 * Parses every route condition in a `routes.json` and prints the tree it produced.
 *
 * **Adam's question, 2026-09-03:** *"we would want to parse the JSON into a NodeExpression in the old
 * one, and see if 3.0.0 has logically equivalent expressions in those routes."*
 *
 * The screens that came before this one walked the JSON, and the JSON is not what either engine holds:
 * `NodeExpression.fromJSON` runs `normalize`, which inserts a `NodeGroup` around a cross-operator left
 * child. A structural reading of the file is therefore answering about a tree that no engine builds.
 *
 * This runs under both jars, on the same file, and re-emits what each one actually built. Comparing the
 * two is left to `compare-conditions.py`, which does it by truth table rather than by shape - two trees
 * that bracket differently and mean the same thing must not count as a difference, and that is the
 * whole point of the question.
 *
 * It touches no layout, opens no window and binds no port: `NodeExpression.fromJSON` is a static
 * function of a `JSONObject`. So it can run against the operator's live file without any of the care
 * `ParityDriver` needs.
 *
 * Usage: ConditionParityDriver &lt;routes.json&gt; &lt;outFile&gt; &lt;label&gt;
 *
 * @author Adam
 */
public class ConditionParityDriver
{
    public static void main(String[] args) throws Exception
    {
        if (args.length < 3)
        {
            System.err.println("Usage: ConditionParityDriver <routes.json> <outFile> <label>");

            System.exit(2);
        }

        File routesFile = new File(args[0]).getAbsoluteFile();
        File outFile = new File(args[1]).getAbsoluteFile();
        String label = args[2];

        if (!routesFile.isFile())
        {
            throw new IllegalStateException("no routes at " + routesFile);
        }

        String text = new String(Files.readAllBytes(routesFile.toPath()), Charset.forName("UTF-8"));

        // Both shapes, because the file has been written by more than one version: an object with a
        // "routes" array, and a bare array.
        JSONArray routes;

        String trimmed = text.trim();

        if (trimmed.startsWith("["))
        {
            routes = new JSONArray(trimmed);
        }
        else
        {
            routes = new JSONObject(trimmed).getJSONArray("routes");
        }

        List<String> lines = new ArrayList<>();

        int parsed = 0;
        int failed = 0;

        for (int i = 0; i < routes.length(); i++)
        {
            JSONObject route = routes.getJSONObject(i);

            String name = route.optString("name", "route " + i);

            if (!route.has("conditions"))
            {
                continue;
            }

            try
            {
                NodeExpression tree = NodeExpression.fromJSON(route.getJSONObject("conditions"));

                if (tree == null)
                {
                    lines.add(join(label, name, "NULL", ""));

                    failed++;

                    continue;
                }

                // Re-emitted rather than described: toJSON is the one rendering both jars are certain
                // to agree about the meaning of, and it is what the comparison reads.
                lines.add(join(label, name, "OK", tree.toJSON().toString()));

                parsed++;
            }
            catch (Exception | Error refused)
            {
                lines.add(join(label, name, "REFUSED", String.valueOf(refused)));

                failed++;
            }
        }

        outFile.getParentFile().mkdirs();

        try (PrintWriter out = new PrintWriter(outFile, "UTF-8"))
        {
            for (String line : lines)
            {
                out.println(line);
            }
        }

        System.out.println(label + ": " + parsed + " condition(s) parsed, " + failed + " refused, from "
            + routes.length() + " route(s) -> " + outFile);
    }

    /** Tab-separated, with tabs and newlines taken out of the parts so a row is always one line. */
    private static String join(String... parts)
    {
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0) out.append('\t');

            out.append(parts[i] == null ? "" : parts[i].replace("\t", " ").replace("\n", " ")
                .replace("\r", " "));
        }

        return out.toString();
    }
}
