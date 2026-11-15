package core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import support.ReportsStarted;

/**
 * The last release's updater takes this build (RELEASE.md section 2): the update code as it is at the newest release tag,
 * compiled from the tag into a class loader of its own, offered a release of this build the way GitHub serves one.
 *
 * Nothing goes to the network: the release is a JSON object listed as GitHub lists v3.0.8's, and its assets local files.
 * The jar is TC_UPDATE_JAR, the one `ant clean package-for-store` built (`docs/tools/gates.sh` builds and passes it);
 * without it a stand-in is built from the classes under test, and the claim says so on its output.  Its version is read
 * from the jar itself, and the version it starts sees only the jar's classes, so a jar missing the update's classes
 * fails.  What cannot be run is TrainControl's own start-up: the started version is `support.ReportsStarted`, which says
 * it started through the jar's `SelfUpdate.reportStarted`; `regression.testTheUpdateIsWiredEndToEnd` pins where
 * TrainControl calls it.  TC_LAST_RELEASE overrides the tag.
 */
public class testTheLastReleaseUpdatesToThisBuild
{
    private File folder;

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception
    {
        if (folder == null || !folder.exists()) return;

        // Anything Windows still holds goes when this JVM ends
        try (java.util.stream.Stream<Path> paths = Files.walk(folder.toPath()))
        {
            paths.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(f ->
            {
                if (!f.delete()) f.deleteOnExit();
            });
        }
    }

    /**
     * Found, compared as newer, downloaded, checked whole, swapped in and heard from, each step by the old code.
     *
     * MUTATION: `RAW_VERSION` set to "3_1_0" (the old parser reads "3"), or to the tag's own version, or
     * `JarUpdater.STARTED_ENVIRONMENT` renamed, or `package-for-store` leaving `SelfUpdate` out of the jar (the old
     * updater never hears the start and puts the old jar back), or `store.jar.name` changed, and this fails.
     */
    @Test(timeOut = 300000)
    public void testTheOldUpdaterFindsDownloadsAndStartsThisBuild() throws Exception
    {
        folder = Files.createTempDirectory("tc-last-release ").toFile();

        String tag = lastReleaseTag();
        File old = new File(folder, "old");

        git("archive", "--format=zip", "-o", new File(folder, "old.zip").getAbsolutePath(), tag, "src", "resources");
        unzip(new File(folder, "old.zip"), old);

        String oldVersion = rawVersionIn(new File(old, "src/org/traincontrol/marklin/MarklinControlStation.java"));

        try (URLClassLoader then = compile(old))
        {
            Class<?> util = then.loadClass("org.traincontrol.util.Util");
            Class<?> selfUpdate = then.loadClass("org.traincontrol.util.SelfUpdate");
            Class<?> updater = then.loadClass("org.traincontrol.util.JarUpdater");
            Class<?> json = then.loadClass("org.json.JSONObject");

            File shipped = theJarThisBuildShips();
            byte[] shippedBytes = Files.readAllBytes(shipped.toPath());

            // Every release so far, v2.6.5 to v3.0.8, carried its jar as TrainControl.jar
            assertEquals(shipped.getName(), "TrainControl.jar", "the release's jar would be named otherwise");

            String version = versionIn(shipped);

            // As GitHub's API lists v3.0.8: the name carries v{RELEASE}, the jar is asset 0 (RELEASE.md); a second
            // asset after it, so taking any but the first fails the whole-jar check
            File other = new File(folder, "Checksums.txt");

            Files.write(other.toPath(), ("sha256 " + sha256(shippedBytes)).getBytes(StandardCharsets.UTF_8));

            JSONObject release = new JSONObject().put("name", "Marklin Train Control v" + version)
                .put("tag_name", "v" + version.replace('.', '_')).put("prerelease", false)
                .put("assets", new JSONArray().put(asset(shipped, shippedBytes)).put(asset(other,
                    Files.readAllBytes(other.toPath()))));

            Object info = json.getConstructor(String.class).newInstance(release.toString());

            String found = (String) util.getMethod("parseReleaseVersion", json).invoke(null, info);

            assertEquals(found, version, tag + "'s updater reads this release's name as another version");

            int newer = (Integer) then.loadClass("org.traincontrol.util.Conversion")
                .getMethod("compareVersions", String.class, String.class).invoke(null, found, oldVersion);

            assertTrue(newer > 0, tag + " (" + oldVersion + ") does not see " + found + " as newer, so it never offers"
                + " the update");

            // Download & Update, as the old flow does it: beside the running jar, then checked before anything moves
            File jar = new File(folder, "TrainControl.jar");

            Files.write(jar.toPath(), ("TrainControl " + oldVersion).getBytes(StandardCharsets.UTF_8));

            File download = (File) selfUpdate.getMethod("downloadTarget", File.class, String.class)
                .invoke(null, jar, found);

            util.getMethod("downloadFile", String.class, File.class)
                .invoke(null, util.getMethod("parseDownloadURL", json).invoke(null, info), download);

            Object whyNot = selfUpdate.getMethod("whyNotAWholeJar", File.class, long.class, String.class).invoke(null,
                download, util.getMethod("parseDownloadSize", json).invoke(null, info),
                util.getMethod("parseDownloadDigest", json).invoke(null, info));

            assertNull(whyNot, tag + "'s updater refuses this build's jar as not whole");

            assertEquals(theOldOutcome(updater, jar, download, oldVersion, found, shippedBytes), "UPDATED",
                tag + "'s updater did not keep this build; its log:\n" + read(new File(folder, "update.log")));

            assertTrue(Arrays.equals(Files.readAllBytes(jar.toPath()), shippedBytes), "the jar is not this build's");
        }
    }

    /** The old `JarUpdater.update`, its real start starting `ReportsStarted` on the swapped-in jar's classes. */
    private String theOldOutcome(Class<?> updater, File jar, File download, String oldVersion, String newVersion,
        byte[] shippedBytes) throws Exception
    {
        ClassLoader then = updater.getClassLoader();
        Class<?> request = then.loadClass("org.traincontrol.util.JarUpdater$Request");
        Class<?> launcher = then.loadClass("org.traincontrol.util.JarUpdater$Launcher");
        Class<?> mover = then.loadClass("org.traincontrol.util.JarUpdater$Mover");
        Class<?> log = then.loadClass("org.traincontrol.util.JarUpdater$Log");
        Class<?> started = then.loadClass("org.traincontrol.util.JarUpdater$Started");

        String java = new File(new File(System.getProperty("java.home"), "bin"),
            File.separatorChar == '\\' ? "java.exe" : "java").getPath();

        Object asked = request.getConstructor(File.class, File.class, File.class, File.class, String.class, String.class,
            File.class, String.class, String.class, List.class, List.class).newInstance(jar, download,
            new File(folder, (String) updater.getField("LOCK_NAME").get(null)),
            new File(folder, (String) updater.getField("RUNNING_NAME").get(null)), oldVersion, newVersion, folder, java,
            "Updating", Collections.emptyList(), Collections.emptyList());

        // What the real launcher `startProcess` wraps, for the Process to wait for: it holds the jar until it ends
        Method startDetached = updater.getMethod("startDetached", List.class, File.class, File.class);
        Method rename = updater.getDeclaredMethod("rename", Path.class, Path.class);

        rename.setAccessible(true);

        List<String> starts = new ArrayList<>();
        List<Process> running = new ArrayList<>();

        Object launching = Proxy.newProxyInstance(then, new Class<?>[] {launcher}, (proxy, method, args) ->
        {
            @SuppressWarnings("unchecked")
            List<String> command = (List<String>) args[0];
            int at = command.indexOf("-jar");

            starts.add(String.valueOf(command));

            assertTrue(at >= 0 && command.get(at + 1).equals(jar.getAbsolutePath()), "the old updater does not start"
                + " the jar it replaced: " + command);
            // A rollback starting the old jar again: nothing to run, and the outcome says so
            if (!Arrays.equals(Files.readAllBytes(jar.toPath()), shippedBytes))
            {
                return Proxy.newProxyInstance(then, new Class<?>[] {started}, (p, m, a) -> false);
            }

            // The jar and ReportsStarted alone: nothing of this build's classes fills a gap in the jar
            String classes = jar.getAbsolutePath() + File.pathSeparator + reportsStartedAlone().getAbsolutePath();

            Process child = (Process) startDetached.invoke(null, Arrays.asList(java, "-cp", classes,
                ReportsStarted.class.getName(), "marker"), args[1], args[2]);

            running.add(child);

            return Proxy.newProxyInstance(then, new Class<?>[] {started}, (p, m, a) -> child.isAlive());
        });

        Object moving = Proxy.newProxyInstance(then, new Class<?>[] {mover}, (proxy, method, args) ->
            rename.invoke(null, args));

        Object outcome = updater.getMethod("update", request, long.class, long.class, int.class, long.class, long.class,
            launcher, mover, log, Runnable.class).invoke(null, asked, 5000L, 20L, 20, 100L, 60000L, launching, moving,
            log.getConstructor(File.class).newInstance(new File(folder, "update.log")), null);

        for (Process child : running) child.waitFor(30, TimeUnit.SECONDS);

        return ((Enum<?>) outcome).name() + (starts.size() == 1 ? "" : ", having started " + starts);
    }

    /**
     * TC_LAST_RELEASE, else the highest `v<number>_<number>...` tag; a beta's does not count, nor the 1.x tags with no
     * underscore (`v11011`), and `gates.sh` picks it the same way.
     */
    private String lastReleaseTag() throws Exception
    {
        String given = System.getenv("TC_LAST_RELEASE");

        if (given != null && !given.trim().isEmpty()) return given.trim();

        String best = null;

        for (String tag : git("tag", "--list", "v*").split("\\R"))
        {
            if (tag.trim().matches("v\\d+(_\\d+)+") && (best == null || newer(tag.trim(), best))) best = tag.trim();
        }

        assertNotNull(best, "no release tag in this repository");

        return best;
    }

    private static boolean newer(String a, String b)
    {
        String[] x = a.substring(1).split("_"), y = b.substring(1).split("_");

        for (int i = 0; i < Math.max(x.length, y.length); i++)
        {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0, q = i < y.length ? Integer.parseInt(y[i]) : 0;

            if (p != q) return p > q;
        }

        return false;
    }

    private static String git(String... args) throws Exception
    {
        List<String> command = new ArrayList<>(Collections.singletonList("git"));

        command.addAll(Arrays.asList(args));

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        process.getOutputStream().close();

        String out = new String(readAll(process.getInputStream()), StandardCharsets.UTF_8);

        assertTrue(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0, command + " failed: " + out);

        return out;
    }

    private static void unzip(File zip, File into) throws Exception
    {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip.toPath())))
        {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry())
            {
                File to = new File(into, entry.getName());

                if (entry.isDirectory()) continue;

                to.getParentFile().mkdirs();
                Files.write(to.toPath(), readAll(in));
            }
        }
    }

    private static String rawVersionIn(File source) throws Exception
    {
        Matcher m = Pattern.compile("RAW_VERSION\\s*=\\s*\"([^\"]+)\"").matcher(read(source));

        assertTrue(m.find(), "no RAW_VERSION in " + source);

        return m.group(1);
    }

    /** The tag's update code and what it needs, compiled; its bundles and libraries from the tag too. */
    private URLClassLoader compile(File old) throws Exception
    {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();

        assertNotNull(javac, "this test needs a JDK, to compile the release's updater");

        List<URL> path = new ArrayList<>();
        List<String> libraries = new ArrayList<>();

        for (File lib : new File(old, "resources").listFiles((d, n) -> n.endsWith(".jar")))
        {
            path.add(lib.toURI().toURL());
            libraries.add(lib.getAbsolutePath());
        }

        File out = new File(old, "classes");
        out.mkdirs();

        List<String> options = new ArrayList<>(Arrays.asList("-nowarn", "-encoding", "UTF-8", "-proc:none", "-d",
            out.getAbsolutePath(), "-cp", String.join(File.pathSeparator, libraries), "-sourcepath",
            new File(old, "src").getAbsolutePath()));

        for (String name : Arrays.asList("Util", "Conversion", "SelfUpdate", "JarUpdater"))
        {
            options.add(new File(old, "src/org/traincontrol/util/" + name + ".java").getAbsolutePath());
        }

        java.io.ByteArrayOutputStream errors = new java.io.ByteArrayOutputStream();

        assertEquals(javac.run(null, errors, errors, options.toArray(new String[0])), 0, "the release's updater did not"
            + " compile: " + errors);

        path.add(0, out.toURI().toURL());
        path.add(new File(old, "src").toURI().toURL());

        // The extension loader as parent: nothing of TrainControl's or org.json may come from this build
        return new URLClassLoader(path.toArray(new URL[0]), ClassLoader.getSystemClassLoader().getParent());
    }

    /** TC_UPDATE_JAR, else this build's classes and resources with the manifest `package-for-store` writes. */
    private File theJarThisBuildShips() throws Exception
    {
        String given = System.getenv("TC_UPDATE_JAR");

        if (given != null && !given.trim().isEmpty()) return new File(given.trim());

        String standIn = "*** STAND-IN JAR: no TC_UPDATE_JAR, so this offers a jar built from the classes under test,"
            + " not the one ant package-for-store builds; docs/tools/gates.sh passes the real one ***";

        System.out.println(standIn);
        System.err.println(standIn);
        org.testng.Reporter.log(standIn);

        Matcher named = Pattern.compile("name=\"store.jar.name\"\\s+value=\"([^\"]+)\"")
            .matcher(read(new File("build.xml")));

        assertTrue(named.find(), "no store.jar.name in build.xml");

        Properties project = new Properties();

        try (InputStream in = Files.newInputStream(new File("nbproject/project.properties").toPath()))
        {
            project.load(in);
        }

        String main = project.getProperty("main.class");

        assertNotNull(main, "no main.class in nbproject/project.properties, so the jar has no Main-Class");

        Manifest manifest = new Manifest();

        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, main);

        File classes = new File(MarklinControlStation.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File jar = new File(new File(folder, "store"), named.group(1) + ".jar");

        jar.getParentFile().mkdirs();

        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar), manifest))
        {
            add(out, classes, classes, ".class");
            add(out, new File("src"), new File("src"), null);

            // The libraries the target merges in, by the names it lists
            Matcher libraries = Pattern.compile("zipgroupfileset dir=\"resources\" includes=\"([^\"]+)\"")
                .matcher(read(new File("build.xml")));

            assertTrue(libraries.find(), "no libraries listed for package-for-store in build.xml");

            for (String library : libraries.group(1).split(","))
            {
                try (ZipInputStream in = new ZipInputStream(Files.newInputStream(new File("resources",
                    library.trim()).toPath())))
                {
                    for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry())
                    {
                        if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) continue;

                        out.putNextEntry(new JarEntry(entry.getName()));
                        out.write(readAll(in));
                        out.closeEntry();
                    }
                }
            }
        }

        return jar;
    }

    /** The application's files under `root` (the `org` tree and the main class), by `ending`, or all but sources. */
    private static void add(JarOutputStream out, File root, File at, String ending) throws Exception
    {
        File[] files = at.listFiles();

        for (File file : files == null ? new File[0] : files)
        {
            String name = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');

            if (!name.startsWith("org") && !name.startsWith("TrainControl")) continue;

            if (file.isDirectory())
            {
                add(out, root, file, ending);
            }
            else if (ending == null ? !name.endsWith(".java") : name.endsWith(ending))
            {
                out.putNextEntry(new JarEntry(name));
                out.write(Files.readAllBytes(file.toPath()));
                out.closeEntry();
            }
        }
    }

    /** The version the jar says it is, read by a class loader that sees the jar alone. */
    private static String versionIn(File jar) throws Exception
    {
        try (URLClassLoader shipped = new URLClassLoader(new URL[] {jar.toURI().toURL()},
            ClassLoader.getSystemClassLoader().getParent()))
        {
            return (String) shipped.loadClass("org.traincontrol.marklin.MarklinControlStation").getField("RAW_VERSION")
                .get(null);
        }
    }

    /** An asset as GitHub's API lists one. */
    private static JSONObject asset(File file, byte[] bytes) throws Exception
    {
        return new JSONObject().put("name", file.getName()).put("content_type", "application/octet-stream")
            .put("browser_download_url", file.toURI().toString()).put("size", bytes.length)
            .put("digest", "sha256:" + sha256(bytes));
    }

    /** A folder holding `ReportsStarted`'s class and nothing else. */
    private File reportsStartedAlone() throws Exception
    {
        String path = ReportsStarted.class.getName().replace('.', '/') + ".class";
        File alone = new File(folder, "reports-started");
        File copy = new File(alone, path);

        if (!copy.exists())
        {
            copy.getParentFile().mkdirs();

            try (InputStream in = ReportsStarted.class.getClassLoader().getResourceAsStream(path))
            {
                Files.write(copy.toPath(), readAll(in));
            }
        }

        return alone;
    }

    private static String sha256(byte[] bytes) throws Exception
    {
        StringBuilder hex = new StringBuilder();

        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) hex.append(String.format("%02x", b & 0xff));

        return hex.toString();
    }

    private static byte[] readAll(InputStream in) throws Exception
    {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[65536];

        for (int n = in.read(buffer); n != -1; n = in.read(buffer)) out.write(buffer, 0, n);

        return out.toByteArray();
    }

    private static String read(File file) throws Exception
    {
        return file.exists() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "(none)";
    }
}
