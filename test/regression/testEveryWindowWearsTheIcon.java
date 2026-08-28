package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * OB-124: every window in the application wears the TrainControl icon.
 *
 * Adam, 2026-08-27: "the popup window for cropping images doesn't have the traincontrol icon in the
 * title bar.  add it back, and check for other popups with the same issue."
 *
 * There were four without one. The icon was being set in seven places, each spelling out the same
 * three-part line - resource, toolkit, setIconImage - and a rule written seven times is a rule the
 * eighth window does not get. That is what happened: `LocIconCropDialog`, `BusyDialog` and two dialogs
 * built inline had never had it, and nothing said so, because a dialog LOOKS as though it should
 * inherit its owner's icon and does not reliably do so.
 *
 * **Fixing the four was the easy half.** This is the half that matters: a window added next month is
 * required to ask, and fails here if it does not. The alternative is finding out the way Adam did.
 *
 * Read from the source rather than by opening windows, because opening every window in the
 * application needs a railway, a display and a great deal of patience - and what can silently go wrong
 * is a new class that never calls this, which is visible in the text.
 *
 * @author Adam
 */
public class testEveryWindowWearsTheIcon
{
    /** What makes a file a window. */
    private static final String[] WINDOWS = {
        "extends JFrame", "extends javax.swing.JFrame",
        "extends JDialog", "extends javax.swing.JDialog",
        "extends PositionAwareJFrame",
        "new JDialog(", "new javax.swing.JDialog(",
    };

    /** Files that are windows and deliberately do not ask, with the reason. */
    private static final String[][] DELIBERATELY_OUT = {
        {"PositionAwareJFrame",
         "a base class that is never shown by itself - every window extending it asks for the icon, "
         + "and this one has no title bar of its own to put one in"},
    };

    /**
     * MUTATION: taking the call out of any one window fails this and names the file.
     */
    @Test
    public void testEveryWindowAsksForTheIcon() throws Exception
    {
        List<String> naked = new ArrayList<>();

        for (File source : javaUnder(new File("src")))
        {
            String body = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);

            if (!isAWindow(body)) continue;

            if (excused(source.getName())) continue;

            // The FORM's own code counts as covered.
            //
            // A window laid out in the GUI builder carries its icon as a property of the form, and the
            // initComponents block that sets it is GENERATED - regenerated whenever anybody opens the
            // form in the designer. Editing that block to call the helper is how this test was first
            // satisfied, and it was wrong: the designer would put it back, and for the helper's own
            // file nothing would ever notice.
            if (generatedCode(body).contains("setIconImage")) continue;

            if (!withoutGeneratedCode(body).contains("applyWindowIcon")) naked.add(source.getName());
        }

        assertTrue(naked.isEmpty(),
            "these windows never ask for the application's icon, so they open with Java's: " + naked
            + ". One call - TrainControlUI.applyWindowIcon(window) - is all it takes, and the reason "
            + "this test exists is that four windows were missing it and nobody could see that from "
            + "the code");
    }

    /**
     * And the incantation is written in ONE place.
     *
     * The four that were missing it were missing it because the rule was copied rather than called:
     * seven windows each loaded the resource and built the image for themselves, so adding a window
     * meant remembering a line nobody had written down as a rule.
     *
     * MUTATION: spelling the resource out again in any window fails this.
     */
    @Test
    public void testTheIconIsLoadedInOnePlaceOnly() throws Exception
    {
        List<String> spelling = new ArrayList<>();

        for (File source : javaUnder(new File("src")))
        {
            String body = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);

            // Hand-written code only.  A window laid out in the designer carries the icon as a form
            // property, and the block that sets it is regenerated from the .form - so naming the
            // resource there is the designer's doing rather than a copy somebody made.
            String written = withoutGeneratedCode(body);

            if (!written.contains("locicon.png")) continue;

            // The one place that is allowed to name it is the helper's own file.
            if (written.contains("public static void applyWindowIcon(")) continue;

            spelling.add(source.getName());
        }

        assertTrue(spelling.isEmpty(),
            "these files load the window icon for themselves rather than calling applyWindowIcon: "
            + spelling + ". That is how the rule came to be written seven times and missed four, "
            + "which is OB-124");
    }

    /**
     * A dialog built inline is a window too, and is asked about ONE AT A TIME.
     *
     * The first version asked per FILE, which is not the same question: one `applyWindowIcon` anywhere
     * in TrainControlUI.java excused every window built in it. Two of the four windows OB-124 was
     * about are inline dialogs in files that already call the helper elsewhere - so the check that
     * found them would not have found the next one.
     *
     * MUTATION: adding a `new JDialog(...)` with no icon call after it fails this.
     */
    @Test
    public void testEveryInlineDialogAsksForItself() throws Exception
    {
        List<String> naked = new ArrayList<>();

        for (File source : javaUnder(new File("src")))
        {
            String body = withoutGeneratedCode(
                new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));

            for (String made : new String[] {"new JDialog(", "new javax.swing.JDialog("})
            {
                for (int at = body.indexOf(made); at >= 0; at = body.indexOf(made, at + 1))
                {
                    // Within reach of the construction, which is where a window is dressed.
                    String after = body.substring(at, Math.min(body.length(), at + REACH));

                    if (!after.contains("applyWindowIcon")) naked.add(source.getName());
                }
            }
        }

        assertTrue(naked.isEmpty(),
            "these dialogs are built without asking for the application's icon, so they open with "
            + "Java's: " + naked + ". A dialog does not reliably inherit its owner's icon, which is "
            + "what made OB-124 invisible from the code");
    }

    /** How far after a dialog is constructed its icon call may sit. */
    private static final int REACH = 900;

    /**
     * Everything between GEN-BEGIN and GEN-END, which belongs to the GUI builder and not to us.
     */
    private String generatedCode(String body)
    {
        StringBuilder out = new StringBuilder();

        for (int at = body.indexOf("GEN-BEGIN"); at >= 0; at = body.indexOf("GEN-BEGIN", at + 1))
        {
            int end = body.indexOf("GEN-END", at);

            out.append(body, at, end < 0 ? body.length() : end);

            if (end < 0) break;
        }

        return out.toString();
    }

    /**
     * The file with its generated blocks removed, so a check reads what a person actually wrote.
     */
    private String withoutGeneratedCode(String body)
    {
        StringBuilder out = new StringBuilder();

        int from = 0;

        while (true)
        {
            int at = body.indexOf("GEN-BEGIN", from);

            if (at < 0) break;

            out.append(body, from, at);

            int end = body.indexOf("GEN-END", at);

            if (end < 0) return out.toString();

            from = end;
        }

        out.append(body.substring(from));

        return out.toString();
    }

    private boolean isAWindow(String body)
    {
        for (String mark : WINDOWS)
        {
            if (body.contains(mark)) return true;
        }

        return false;
    }

    private boolean excused(String fileName)
    {
        for (String[] out : DELIBERATELY_OUT)
        {
            if (fileName.equals(out[0] + ".java")) return true;
        }

        return false;
    }

    private List<File> javaUnder(File dir) throws Exception
    {
        List<File> found = new ArrayList<>();

        File[] children = dir.listFiles();

        if (children == null) return found;

        for (File child : children)
        {
            if (child.isDirectory()) found.addAll(javaUnder(child));
            else if (child.getName().endsWith(".java")) found.add(child);
        }

        return found;
    }
}
