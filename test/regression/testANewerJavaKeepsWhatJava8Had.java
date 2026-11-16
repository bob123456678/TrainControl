package regression;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.prefs.Preferences;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinLocomotiveCsv;
import org.traincontrol.util.Conversion;
import org.traincontrol.util.Util;
import support.Reflect;

/**
 * The jar still built for Java 8 looks and remembers the same on Java 9 and later (FR-236): the window is scaled once,
 * not by Java and again by the application, and what Java 8 read in the machine's own code page (the folder key the
 * settings are saved under, a CSV that is not UTF-8) is still read in it where Java 18 and later default to UTF-8.
 * Java 18 is stood in for by setting `native.encoding`, the property it names the machine's code page in.
 */
public class testANewerJavaKeepsWhatJava8Had
{
    /** A folder with a Danish letter, and the last ten digits of its key on Java 8 (Windows-1252) and on Java 21. */
    private static final String JORGEN = "C:\\Users\\J\u00f8rgen\\Desktop\\TrainControl";
    private static final String JAVA8_KEY = "49873a543b";
    private static final String JAVA21_KEY = "b7c5101b3d";

    /**
     * On Java 8 the scale is the display's, as before; on Windows, where Java 9 and later scale the window themselves,
     * it is 1, so 125% is drawn at 125% and not at about 156%.  On a Mac or Linux the dpi is not the display's scale,
     * so Java 9 and later keep 3.1.0's dpi / 96 there.
     *
     * MUTATION: `!javaScales` for `!(javaScales && windows)` in `TrainControlUI.uiScaleFor`, and a Mac's scale changes.
     */
    @Test(timeOut = 60000)
    public void testTheWindowIsScaledOnceOnEveryJava() throws Exception
    {
        java.lang.reflect.Method scale = Reflect.method(org.traincontrol.gui.TrainControlUI.class, "uiScaleFor",
            int.class, boolean.class, boolean.class);

        int[] dpis = {96, 120, 144, 0};
        String[] display = {"1.0", "1.25", "1.5", "1.0"};

        for (int i = 0; i < dpis.length; i++)
        {
            assertEquals(scale.invoke(null, dpis[i], false, true), display[i], "Java 8's scale at " + dpis[i] + " dpi");
            assertEquals(scale.invoke(null, dpis[i], false, false), display[i], "Java 8's scale off Windows");
            assertEquals(scale.invoke(null, dpis[i], true, false), display[i],
                "off Windows, Java 9 and later no longer get 3.1.0's scale at " + dpis[i] + " dpi");
            assertEquals(scale.invoke(null, dpis[i], true, true), "1.0",
                "where Java scales the window itself, the application adds a scale of its own at " + dpis[i] + " dpi");
        }
    }

    /**
     * A folder is keyed by its path in the machine's code page, as on Java 8: Java 21 hashed the Danish folder in UTF-8,
     * and its saved IP, layout and window positions looked reset.
     *
     * MUTATION: hash in `Charset.defaultCharset()` in `Conversion.getFolderHash`, and the key is not the native one.
     */
    @Test(timeOut = 60000)
    public void testAFolderIsKeyedAsJava8KeyedIt() throws Exception
    {
        assertEquals(key(JORGEN, Charset.forName("windows-1252")), JAVA8_KEY);
        assertEquals(key(JORGEN, StandardCharsets.UTF_8), JAVA21_KEY);

        String here = new File("").getAbsolutePath();
        String was = System.getProperty("native.encoding");

        assertEquals(Conversion.getFolderHash(10), key(here, Charset.defaultCharset()), "Java 8's key changed");

        // Through the door every key is made by: UTF-16 stands in for a code page this folder hashes differently in.
        String inNative = key(here, StandardCharsets.UTF_16LE);
        Preferences node = isolatedNode();

        try
        {
            System.setProperty("native.encoding", "UTF-16LE");
            node.put("FR236" + inNative, "x");

            assertEquals(Conversion.getFolderHash(10), inNative,
                "the folder's key is not its path in the machine's code page");
        }
        finally
        {
            node.remove("FR236" + inNative);
            restore("native.encoding", was);
        }
    }

    /**
     * Settings a Java 18 or later saved under the UTF-8 key stay found while the native key has none.
     *
     * MUTATION: return the native hash unconditionally from `Conversion.getFolderHash`, and they are lost.
     */
    @Test(timeOut = 60000)
    public void testSettingsSavedByANewerJavaAreStillFound() throws Exception
    {
        String here = new File("").getAbsolutePath();
        String inUtf8 = key(here, StandardCharsets.UTF_8);
        Preferences node = isolatedNode();
        String was = System.getProperty("native.encoding");

        try
        {
            System.setProperty("native.encoding", "UTF-16LE");
            node.put("FR236" + inUtf8, "x");

            assertNotEquals(key(here, StandardCharsets.UTF_16LE), inUtf8, "precondition");
            assertEquals(Conversion.getFolderHash(10), inUtf8, "the settings saved under the UTF-8 key were dropped");
        }
        finally
        {
            node.remove("FR236" + inUtf8);
            restore("native.encoding", was);
        }
    }

    /**
     * A folder neither key has settings for takes the native key, so a first start on Java 18 or later saves where
     * Java 8 will look.  The run's own keys under either hash are lifted off for the claim and put back.
     *
     * MUTATION: drop `|| !hasSettings(java18)` from `Conversion.getFolderHash`, and a fresh folder takes the UTF-8 key.
     */
    @Test(timeOut = 60000)
    public void testAFreshFolderTakesTheNativeKey() throws Exception
    {
        String here = new File("").getAbsolutePath();
        String inNative = key(here, StandardCharsets.UTF_16LE), inUtf8 = key(here, StandardCharsets.UTF_8);
        Preferences node = isolatedNode();
        java.util.Map<String, String> lifted = new java.util.HashMap<>();
        String was = System.getProperty("native.encoding");

        for (String k : node.keys()) if (k.endsWith(inNative) || k.endsWith(inUtf8)) lifted.put(k, node.get(k, null));

        try
        {
            for (String k : lifted.keySet()) node.remove(k);

            System.setProperty("native.encoding", "UTF-16LE");

            assertEquals(Conversion.getFolderHash(10), inNative, "a folder with no settings at all took the UTF-8 key");
        }
        finally
        {
            lifted.forEach(node::put);
            restore("native.encoding", was);
        }
    }

    /**
     * Both jars the build makes export the shell's folders, for the file chooser's Look In list on Java 9 and later:
     * the store jar's manifest in build.xml and the NetBeans jar's manifest.mf.  Java 8 ignores the attribute.
     *
     * MUTATION: drop the Add-Exports attribute from build.xml's package-for-store manifest, and this fails.
     */
    @Test(timeOut = 60000)
    public void testBothJarsExportTheShellsFolders() throws Exception
    {
        String build = new String(java.nio.file.Files.readAllBytes(new File("build.xml").toPath()),
            StandardCharsets.UTF_8);
        int store = build.indexOf("<target name=\"package-for-store\"");
        int from = build.indexOf("<manifest>", store), to = build.indexOf("</manifest>", from);

        assertTrue(store >= 0 && from > store && to > from, "build.xml's package-for-store has no manifest");
        assertTrue(build.substring(from, to).contains(
            "<attribute name=\"Add-Exports\" value=\"java.desktop/sun.awt.shell\"/>"),
            "the store jar does not export sun.awt.shell, so the Look In list shows only the Desktop on Java 9+");

        try (java.io.InputStream in = new java.io.FileInputStream("manifest.mf"))
        {
            assertEquals(new java.util.jar.Manifest(in).getMainAttributes().getValue("Add-Exports"),
                "java.desktop/sun.awt.shell", "manifest.mf does not export sun.awt.shell");
        }
    }

    /**
     * A CSV that is not UTF-8 is read in the machine's code page, as on Java 8: on a Polish machine Java 21 read it in
     * Windows-1252.
     *
     * MUTATION: `fallbackFor(Charset.defaultCharset())` in `MarklinLocomotiveCsv.decode`, and the letters are wrong.
     */
    @Test(timeOut = 60000)
    public void testACsvIsReadInTheMachinesCodePage() throws Exception
    {
        String polish = "\u0141\u00f3d\u017a";
        String was = System.getProperty("native.encoding");

        try
        {
            System.setProperty("native.encoding", "windows-1250");

            assertEquals(MarklinLocomotiveCsv.decode(polish.getBytes("windows-1250")), polish);
        }
        finally
        {
            restore("native.encoding", was);
        }
    }

    /** The run's own preference node for the window class's settings: never the operator's. */
    private static Preferences isolatedNode()
    {
        String run = System.getProperty(Util.PREFERENCES_PROPERTY);

        assertTrue(run != null && !run.trim().isEmpty(),
            "no isolated preferences: run this class through docs/tools/one.sh, which names the run's own node");

        return Util.preferencesFor(org.traincontrol.gui.TrainControlUI.class);
    }

    /** The last ten hex digits of the SHA-1 of a path in this charset, the folder key's recipe. */
    private static String key(String path, Charset charset) throws Exception
    {
        StringBuilder hex = new StringBuilder();

        for (byte b : java.security.MessageDigest.getInstance("SHA1").digest(path.getBytes(charset)))
        {
            hex.append(String.format("%02x", b & 0xff));
        }

        return hex.substring(hex.length() - 10);
    }

    private static void restore(String property, String was)
    {
        if (was == null) System.clearProperty(property);
        else System.setProperty(property, was);
    }
}
