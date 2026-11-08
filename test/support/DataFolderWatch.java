package support;

import java.util.Map;
import java.util.TreeSet;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlTest;

/**
 * Says which class left the run's saved data changed (`SavedData`): the classes after it in a battery lane read the
 * same data folder.  Fingerprints it when the suite starts and finishes and prints one `MARKER` line naming the
 * class and the files; the runners (docs/tools/one.sh, battery.sh) report that class as failed.  Silent without
 * `traincontrol.dataDir`, where the files would be the operator's own.
 *
 * Test code only: it reads files and touches nothing.
 */
public class DataFolderWatch implements ISuiteListener
{
    /** What the runners look for in a class's output. */
    public static final String MARKER = "*** SAVED DATA LEFT CHANGED";

    private Map<String, String> before;

    @Override
    public void onStart(ISuite suite)
    {
        if (!SavedData.isolated()) return;

        try
        {
            before = SavedData.fingerprint();
        }
        catch (java.io.IOException e)
        {
            throw new IllegalStateException("could not read the saved data", e);
        }
    }

    @Override
    public void onFinish(ISuite suite)
    {
        if (before == null) return;

        try
        {
            Map<String, String> after = SavedData.fingerprint();

            TreeSet<String> changed = new TreeSet<>();

            for (String name : after.keySet())
            {
                if (!after.get(name).equals(before.get(name))) changed.add(name);
            }

            for (String name : before.keySet())
            {
                if (!after.containsKey(name)) changed.add(name);
            }

            if (!changed.isEmpty())
            {
                System.out.println(MARKER + " by " + classes(suite) + ": " + String.join(", ", changed) + " ***");
            }
        }
        catch (java.io.IOException e)
        {
            throw new IllegalStateException("could not read the saved data", e);
        }
    }

    private static String classes(ISuite suite)
    {
        TreeSet<String> names = new TreeSet<>();

        for (XmlTest test : suite.getXmlSuite().getTests())
        {
            for (XmlClass c : test.getClasses()) names.add(c.getName());
        }

        return String.join(", ", names);
    }
}
