package core;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import support.SetupDump;

/**
 * The instrument behind FR-111's "old setups unchanged": `support.SetupDump`, run by `docs/tools/old-setups.sh` on a base
 * build and on the working tree over every test layout, the autonomy format fixtures and a copy of the live layout.
 * Copied into the older build by that script, so it may ask only what every build since 3.0.8 has.
 *
 * With no `TC_OLD_SETUPS_OUT` it proves the instrument: two dumps of one setup match, so a difference between builds is
 * the builds', and a real change of setting shows.
 *
 * @author Adam
 */
public class testSetupDumpComparesBuilds
{
    private static final SetupDump.Sample SMALL = new SetupDump.Sample(4, 8, 6);

    /** The layout fixtures, by folder under the project root. */
    private static final List<String> LAYOUTS = Arrays.asList(
        "test/layouts/single-switch",
        "test/layouts/curve-into-platform",
        "test/layouts/one-page",
        "test/layouts/unfolded-route-tiles",
        "test/layouts/live-snapshot",
        "test/layout",
        "test/layout_subpage",
        "test/test_layout",
        "test/test_layout_snapshot",
        "test/operator_layout",
        "test/baseline/layout",
        "test/cs3_v261");

    /** The autonomy format fixtures, each read straight by `parseAuto` over the layout named beside it. */
    private static final String[][] FORMATS =
    {
        {"test/autonomy.json", "test/test_layout"},
        {"test/sample_autonomy.json", "test/test_layout"},
        {"test/autonomy_sanity.json", "test/test_layout"},
        {"test/autonomy_formats/autonomy-2.7.4c.json", "test/test_layout"},
        {"test/autonomy_formats/legacy-graph.json", "test/test_layout"},
        {"test/autonomy_formats/legacy-graph-sample-layout.json", "test/layouts/live-snapshot"},
        {"test/baseline/configuration.json", "test/baseline/layout"}
    };

    /**
     * MUTATION: have `SetupDump.writeRuns` choose by an unseeded `Random`, and this fails on a railway with a choice.
     */
    @Test
    public void testTheDumpIsTheSameTwiceAndSeesAChange() throws Exception
    {
        String out = System.getenv("TC_OLD_SETUPS_OUT");

        if (out != null)
        {
            dumpEverything(new File(out));

            return;
        }

        File temp = Files.createTempDirectory("tc-setup-dump").toFile();

        File single = new File("test/layouts/single-switch");

        Map<String, Integer> counts = SetupDump.layout(single, new File(temp, "first"), SMALL);

        SetupDump.layout(single, new File(temp, "second"), SMALL);

        assertTrue(counts.get("path answers") > 0, "the dump asked nothing: " + counts);
        assertTrue(counts.get("simulated trips") > 0, "the dump ran nothing: " + counts);

        for (String section : Arrays.asList("settings.txt", "graph.txt", "paths.txt", "offers.txt", "runs.txt",
            "railway.json", "saved-unchanged/setup.json", "saved-captured/configuration-Main.json"))
        {
            assertEquals(read(new File(temp, "second/" + section)), read(new File(temp, "first/" + section)),
                "two dumps of one setup differ in " + section + ", so a difference between builds would prove nothing");
        }
    }

    private static String read(File file) throws Exception
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** Everything, with the sample sized to the railway; the counts go in `counts.txt` for the comparison to report. */
    private static void dumpEverything(File out) throws Exception
    {
        out.mkdirs();

        // Large enough to be every locomotive and route on the small railways; stated in the comparison's report
        SetupDump.Sample sample = new SetupDump.Sample(sizeOf("TC_OLD_SETUPS_LOCOS", 40),
            sizeOf("TC_OLD_SETUPS_ROUTES", 12), sizeOf("TC_OLD_SETUPS_TRIPS", 30));

        List<String> report = new ArrayList<>();

        List<String> folders = new ArrayList<>(LAYOUTS);

        String live = System.getenv("TC_OLD_SETUPS_LIVE");

        if (live != null) folders.add(live);

        for (String folder : folders)
        {
            String label = folder.equals(live) ? "live" : folder.replace('/', '_');

            report.add(label + " " + attempt(() -> SetupDump.layout(new File(folder), new File(out, label), sample)));
        }

        for (String[] format : FORMATS)
        {
            String label = format[0].replace('/', '_');

            report.add(label + " " + attempt(() -> SetupDump.json(new File(format[1]), new File(format[0]),
                new File(out, label), sample)));
        }

        Collections.sort(report);

        try (PrintWriter lines = new PrintWriter(new File(out, "counts.txt"), "UTF-8"))
        {
            lines.println("sample: " + sample.locomotives + " locomotives, " + sample.alternatives + " routes a pair, "
                + sample.trips + " trips");

            for (String line : report) lines.println(line);
        }
    }

    private interface Dump
    {
        Map<String, Integer> run() throws Exception;
    }

    /** A fixture that will not load is an answer to compare too, not a reason to stop. */
    private static String attempt(Dump dump)
    {
        try
        {
            return String.valueOf(dump.run());
        }
        catch (Exception | Error failed)
        {
            return "failed: " + failed;
        }
    }

    private static int sizeOf(String variable, int otherwise)
    {
        String value = System.getenv(variable);

        return value == null ? otherwise : Integer.parseInt(value);
    }
}
