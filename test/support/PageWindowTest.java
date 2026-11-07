package support;

import javax.swing.SwingUtilities;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * The lifecycle of a test that opens the main window and the layout editor over a sandboxed copy of the test layout:
 * the sandbox before each test (so a test can rewrite its index first), the window on request, and everything closed
 * after it.
 */
public abstract class PageWindowTest
{
    protected LayoutSandbox sandbox;
    protected MarklinControlStation model;
    protected TrainControlUI ui;

    @BeforeMethod
    public void openTheSandbox(java.lang.reflect.Method test) throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the main window needs a display");

        sandbox = LayoutSandbox.open(fixtureFor(test));
    }

    /** The layout folder a test's sandbox is a copy of: the test layout, unless a class says otherwise. */
    protected java.io.File fixtureFor(java.lang.reflect.Method test)
    {
        return new java.io.File("test/test_layout");
    }

    @AfterMethod(alwaysRun = true)
    public void closeTheWindow() throws Exception
    {
        try
        {
            stopTheWindow();
        }
        finally
        {
            if (sandbox != null) sandbox.close();

            sandbox = null;
        }
    }

    /** Opens the window over the sandbox and waits for the layout's pages. */
    protected void startTheWindow(final int pages) throws Exception
    {
        model = TestStation.simulatedWithWindow();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        assertTrue(Components.waitFor(() -> model.getLayoutList().size() == pages, 10000), "precondition: the layout"
            + " does not have its " + pages + " pages: " + model.getLayoutList());

        Components.settle();
    }

    /** Closes the editors, the window and the station. */
    protected void stopTheWindow() throws Exception
    {
        try
        {
            EditorsOpen.closeAll();

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(closing::dispose);
            }

            if (model != null) model.stop();
        }
        finally
        {
            ui = null;
            model = null;
        }
    }

    /** Every file in the layout's folder, by its path there, with a digest of its bytes. */
    protected java.util.Map<String, String> fingerprint() throws Exception
    {
        java.util.Map<String, String> files = new java.util.TreeMap<>();

        java.nio.file.Path root = sandbox.getFolder().toPath();

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");

        java.util.List<java.nio.file.Path> found = new java.util.ArrayList<>();

        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root))
        {
            walk.filter(java.nio.file.Files::isRegularFile).forEach(found::add);
        }

        for (java.nio.file.Path file : found)
        {
            files.put(root.relativize(file).toString().replace('\\', '/'),
                java.util.Base64.getEncoder().encodeToString(digest.digest(java.nio.file.Files.readAllBytes(file))));
        }

        return files;
    }
}
