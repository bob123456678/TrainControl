package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.util.JarUpdater;
import org.traincontrol.util.SelfUpdate;
import support.SourceOf;

/**
 * One-click updates are wired together end to end (FR-125, the validation of 2026-10-13).
 *
 * `core.testTheUpdaterSwapsTheJar` hands the updater its launcher, mover and starter, so it says nothing about the
 * real ones or about the places that call them; every one of these could be broken with those claims green:
 *
 * - the started marker: TrainControl writes it once its window is up, or when the user leaves from one of its own
 *   questions, and nowhere earlier - so a version that fails in its own start-up is put back;
 * - the environment variable that tells the new version where to write it, which the real launcher sets for the new
 *   version and the real starter removes for the updater;
 * - the updater's first act, the running marker the old TrainControl waits for before it exits;
 * - the exit's own order: the update is taken only after everything that can refuse the exit, it starts the updater
 *   through the real starter, and a failure to start returns before the editor is let go;
 * - the update's last step: it waits out an open dialog, asks what the Auto tab asks, and leaves through that exit;
 * - one backup at a time, the menu's and the update's;
 * - the Java options carried into the restart, less a debugger.
 *
 * **Two kinds of claim.**  The real launcher and starter are run, against plain JVMs of `support.ReportsStarted` -
 * nothing of TrainControl's but `SelfUpdate`, no jar, nothing outside a temporary folder.  The places that call them
 * cannot be run without starting TrainControl or ending this JVM, so their order is read from the source, as
 * `regression.testEditorSurfaceRules` reads `WindowClosed`'s.  Those are pinned only by reading: a claim that reads
 * the order catches a line moved or dropped, not one that behaves differently at the same place.
 */
public class testTheUpdateIsWiredEndToEnd
{
    private File folder;

    @BeforeMethod
    public void setUp() throws Exception
    {
        folder = Files.createTempDirectory("tc-fr125-wired ").toFile();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception
    {
        if (folder != null && folder.exists())
        {
            Files.walk(folder.toPath())
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    /**
     * The real launcher hands the started marker to the version it starts, through the variable `reportStarted`
     * reads, and that version's report is what `waitForTheStart` hears.
     *
     * MUTATION: `prepareEnvironment` not setting the variable, or `reportStarted` reading another, and this fails.
     *
     * @throws Exception from the process
     */
    @Test
    public void testTheRealLauncherHandsTheMarkerToTheVersionItStarts() throws Exception
    {
        File marker = new File(folder, JarUpdater.STARTED_NAME);

        JarUpdater.Started started = JarUpdater.startProcess(child("marker"), folder, marker);

        assertEquals(JarUpdater.waitForTheStart(marker, started, 30000, 50), JarUpdater.Start.STARTED, "a version"
            + " started by the real launcher reported, and the updater did not hear it: every real update would be"
            + " kept unconfirmed after ten minutes, or put back if quit sooner");
    }

    /**
     * The real starter starts the updater without the started marker's variable - even from inside a TrainControl
     * that an updater started with one - and can end an updater that never says it is running.
     *
     * MUTATION: `startDetached` not removing the variable, and the relayed probe sees it; `stop` not ending the
     * process, and the second half fails.
     *
     * @throws Exception from the processes
     */
    @Test
    public void testTheRealStarterStartsTheUpdaterCleanAndCanStopIt() throws Exception
    {
        // Through the real launcher with a marker, as the updater starts the new version; that version starts the
        // probe through the real starter, as it would start its own updater
        File relayed = new File(folder, "relayed.txt");

        JarUpdater.Started relay = JarUpdater.startProcess(child("relay", relayed.getPath()), folder,
            new File(folder, JarUpdater.STARTED_NAME));

        long until = System.currentTimeMillis() + 40000;

        while (relay.isAlive() && System.currentTimeMillis() < until) Thread.sleep(50);

        assertTrue(relayed.isFile(), "precondition: the relayed probe never ran");

        assertEquals(read(relayed), "STARTED_ENV=null", "the updater inherits the started marker of the run that"
            + " started TrainControl, which names a file that belongs to that run");

        // And an updater that never answers can be ended
        File probed = new File(folder, "probed.txt");

        SelfUpdate.Updater probe = SelfUpdate.startDetached(child("probe", probed.getPath(), "60000"), folder);

        try
        {
            until = System.currentTimeMillis() + 20000;

            while (!probed.isFile() && probe.isAlive() && System.currentTimeMillis() < until) Thread.sleep(50);

            assertTrue(probe.isAlive(), "precondition: the started probe is not running");

            probe.stop();

            until = System.currentTimeMillis() + 20000;

            while (probe.isAlive() && System.currentTimeMillis() < until) Thread.sleep(50);

            assertFalse(probe.isAlive(), "an updater that never said it was running could not be ended, so it could"
                + " take the lock later and swap the jar under a TrainControl that stayed open");
        }
        finally
        {
            if (probe.isAlive()) probe.stop();
        }
    }

    /**
     * The variable is set for the start that is to write the marker and removed for every other, a stale one included.
     */
    @Test
    public void testTheEnvironmentIsPreparedPerStart()
    {
        Map<String, String> environment = new HashMap<>();

        environment.put(JarUpdater.STARTED_ENVIRONMENT, "an earlier run's marker");

        File marker = new File(folder, JarUpdater.STARTED_NAME);

        JarUpdater.prepareEnvironment(environment, marker);

        assertEquals(environment.get(JarUpdater.STARTED_ENVIRONMENT), marker.getAbsolutePath());

        JarUpdater.prepareEnvironment(environment, null);

        assertFalse(environment.containsKey(JarUpdater.STARTED_ENVIRONMENT), "a start with no marker of its own kept"
            + " one it inherited");
    }

    /**
     * The Java options carried into the restart keep a forced language and a memory size, and drop a debugger or an
     * agent attached to this run.
     */
    @Test
    public void testTheRestartKeepsTheOptionsButNotADebugger()
    {
        List<String> kept = SelfUpdate.javaOptions(Arrays.asList("-Duser.language=de", "-Xmx2g",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005", "-javaagent:probe.jar",
            "-agentpath:/x/agent.so", "-Xdebug", "-Xrunjdwp:transport=dt_socket"));

        assertEquals(kept, Arrays.asList("-Duser.language=de", "-Xmx2g"), "a debugger waiting on a port would hold the"
            + " restarted TrainControl until somebody connected to it");
    }

    /**
     * TrainControl says it started once its window is on screen, or when the user leaves from its own question
     * (the IP address cancelled, the first sync given up) - and not as main's first act, which a version that then
     * failed in its own start-up passed.  Pinned by reading.
     *
     * MUTATION: put `SelfUpdate.reportStarted()` back at the top of `TrainControl.main`, or drop any of the three
     * calls, and this fails.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheMarkerIsWrittenWhereTheVersionIsUp() throws Exception
    {
        // `main` is the application's and the updater's and the examples': cut out of TrainControl's own file
        String main = mainOf("public class TrainControl\n");

        assertFalse(main.contains("reportStarted"), "TrainControl.main reports the start before init has run, so a"
            + " version that fails in its own start-up counts as started and is kept");

        String init = bodyOf("public static MarklinControlStation init(String initIP,");

        int display = init.indexOf("theUI.display()");
        int report = init.indexOf("SelfUpdate.reportStarted()", display);

        assertTrue(display >= 0 && report > display, "init does not report the start after the window is shown");

        assertFalse(init.substring(display, report).contains("});"), "the start is not reported in the same step"
            + " that shows the window");

        // Split, so the look-and-feel check (testEveryWindowWearsTheApplicationsLook) does not read this search for the
        // dialog as a dialog this class opens: it opens none
        int cancelled = init.indexOf("JOptionPane" + ".showMessageDialog(null, I18n.t(\"ui.noIpEnteredShutdown\"))");

        assertTrue(cancelled >= 0, "precondition: the IP question's cancel is not where it was");

        // Before ITS exit, not merely somewhere after it: the window's own report comes later in the same method
        String leaving = init.substring(cancelled, init.indexOf("System.exit(1)", cancelled));

        assertTrue(leaving.contains("SelfUpdate.reportStarted()"), "cancelling the IP address leaves without saying"
            + " the version started, so an update into it is put back for the user's own choice");

        inOrder(bodyOf("public static void exitUnconnected(java.util.prefs.Preferences prefs, Runnable exit)"),
            "the first sync given up", "SelfUpdate.reportStarted()", "exit.run()");
    }

    /**
     * The updater's first act is the running marker the old TrainControl waits for, and it runs the update with the
     * real launcher.  Pinned by reading.
     *
     * MUTATION: move `writeMarker(request.running)` after `update(...)`, and this fails - as every real update would,
     * refused after fifteen seconds.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheUpdaterSaysItRunsFirst() throws Exception
    {
        String main = mainOf("public final class JarUpdater\n");

        inOrder(main, "the updater's main", "Request.parse(args)", "writeMarker(request.running)", "update(request");

        assertTrue(main.contains("JarUpdater::startProcess"), "the updater does not start versions with the real"
            + " launcher, which is the one that hands them the started marker");
    }

    /**
     * The exit takes the update only after everything that can refuse it, starts the updater through the real starter
     * and waits to hear from it, returns before letting the editor go when it cannot, and only then exits.  Pinned by
     * reading.
     *
     * MUTATION: move the `take()` above `everyOpenWindowMaySettle`, or drop the `return` after the updater fails to
     * start, and this fails.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheExitStartsTheUpdaterLastOfAll() throws Exception
    {
        String exit = bodyOf("private void WindowClosed(java.awt.event.WindowEvent evt)");

        inOrder(exit, "the exit", "everyOpenWindowMaySettle()", "confirmExitAutonomyRunning", "model.saveState(false)",
            "this.updateHandOver.take()", "SelfUpdate.startUpdater(", "completeExitDiscard()", "System.exit(0)");

        String starting = exit.substring(exit.indexOf("SelfUpdate.startUpdater("), exit.indexOf("completeExitDiscard()"));

        assertTrue(starting.contains("SelfUpdate::startDetached"), "the exit does not start the updater with the real"
            + " starter");
        assertTrue(starting.contains("SelfUpdate.UPDATER_WAIT_MS"), "the exit does not wait to hear from the updater");
        assertTrue(starting.contains("return;"), "an updater that could not start does not stop the exit: TrainControl"
            + " would close with no updater to replace it");
    }

    /**
     * The update's last step waits out an open dialog, asks what the Auto tab asks, and leaves through the exit with
     * the update armed only for as long as that exit runs; the update is one at a time from its question on.  Pinned
     * by reading.
     *
     * MUTATION: ask `isAutonomyBusy` in `whyThisUpdateMustWait` instead of `theAutoTabWaits`, or call
     * `closeNowForTheUpdate` without `whenNoModalDialog`, and this fails.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheUpdateLeavesThroughTheExitAsTheAutoTabAsks() throws Exception
    {
        String close = bodyOf("private void closeForTheUpdate(");

        assertTrue(close.contains("whenNoModalDialog(") && close.contains("closeNowForTheUpdate(")
            && close.contains("aModalDialogIsShowing"), "the update's exit no longer waits for an open dialog, so"
            + " System.exit can close one with the typing in it unapplied");

        inOrder(bodyOf("private void closeNowForTheUpdate("), "the update's exit", "whyThisUpdateMustWait()",
            "updateHandOver.exitWith(", "WindowClosed(null)");

        String why = bodyOf("private String whyThisUpdateMustWait()");

        assertTrue(why.contains("theAutoTabWaits()") && why.contains("stagingFlowActive"), "the update asks less than"
            + " the Auto tab does, so a hand send between its click and its thread is not refused");

        inOrder(bodyOf("private void downloadTheUpdate("), "Download & Update", "updateHandOver.begin()",
            "whyThisUpdateMustWait()");

        inOrder(bodyOf("private boolean installTheDownload("), "the install", "whyNotAWholeJar(", "backupData()",
            "closeForTheUpdate(");
    }

    /**
     * One backup at a time: the menu's and the update's both go through `backupData`, which holds one lock while it
     * writes.  Pinned by reading.
     *
     * MUTATION: call `writeTheBackup` from the update, or drop the `synchronized`, and this fails.
     *
     * @throws Exception from the files
     */
    @Test
    public void testOneBackupAtATime() throws Exception
    {
        String backup = bodyOf("BackupAction.BackupOutcome backupData()");

        assertTrue(backup.contains("synchronized (this.oneBackupAtATime)") && backup.contains("writeTheBackup()"),
            "backupData no longer holds one backup at a time");

        // Asked of the whole program's code: the declaration and the one call.
        int writes = SourceOf.occurrences("writeTheBackup(");

        assertEquals(writes, 2, "something other than backupData writes a backup, around its lock");

        assertTrue(bodyOf("void backupDataMenuItemActionPerformed()").contains("backupData()"),
            "File > Backup does not go through backupData");
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * A command that runs `support.ReportsStarted` in a plain JVM of this test's own Java.
     */
    private static List<String> child(String... args) throws Exception
    {
        String java = new File(new File(System.getProperty("java.home"), "bin"),
            File.separatorChar == '\\' ? "java.exe" : "java").getPath();

        // Absolute: the child runs in the temporary folder, and the runner's classpath names folders from the root
        StringBuilder classpath = new StringBuilder();

        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator))
        {
            if (classpath.length() > 0) classpath.append(File.pathSeparator);

            classpath.append(new File(entry).getAbsolutePath());
        }

        List<String> command = new java.util.ArrayList<>(Arrays.asList(java, "-cp", classpath.toString(),
            support.ReportsStarted.class.getName()));

        command.addAll(Arrays.asList(args));

        return command;
    }

    /**
     * The needles appear in this order, each after the one before.
     */
    private static void inOrder(String body, String what, String... needles)
    {
        int from = 0;

        for (String needle : needles)
        {
            int at = body.indexOf(needle, from);

            assertTrue(at >= 0, "in " + what + ", `" + needle + "` is missing, or is no longer after the step before it"
                + " - the order is " + Arrays.toString(needles));

            from = at + needle.length();
        }
    }

    /**
     * A declaration's code, found wherever the program keeps it, without its comments and with its strings kept.
     */
    private static String bodyOf(String signature)
    {
        return SourceOf.withoutComments(SourceOf.declaration(signature));
    }

    /**
     * The `main` of the class whose declaration starts with `classSignature`, without its comments.
     */
    private static String mainOf(String classSignature)
    {
        return SourceOf.withoutComments(SourceOf.declarationIn(SourceOf.fileDeclaring(classSignature),
            "public static void main(String[] args)"));
    }

    private static String read(File file) throws Exception
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
