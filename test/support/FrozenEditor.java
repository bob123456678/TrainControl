package support;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.testng.Assert;
import org.testng.SkipException;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;

/**
 * The autonomy editor on the frozen railway (never `cs2_sample_layout`, OB-111), in a sandbox, for claims that press its
 * controls: the window behind it hidden, the setup put back before each claim, and what Save wrote read back.
 */
public final class FrozenEditor implements AutoCloseable
{
    public final LayoutSandbox sandbox;
    public final MarklinControlStation model;
    public final TrainControlUI ui;
    public final AutonomySession session;
    public final org.json.JSONObject asOpened;

    private FrozenEditor(LayoutSandbox sandbox, MarklinControlStation model, TrainControlUI ui)
    {
        this.sandbox = sandbox;
        this.model = model;
        this.ui = ui;
        this.session = ui.getAutonomySession();
        this.asOpened = session == null ? null : Components.onTheEventThread(() -> session.snapshotSetup());
    }

    /** Opens the railway in its hidden window; skips the class on a machine with no display. */
    public static FrozenEditor open() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the autonomy editor needs a display");

        LayoutSandbox sandbox = null;

        try
        {
            sandbox = LayoutSandbox.open(Scenario.folderFor("live-snapshot"));

            MarklinControlStation model = TestStation.simulatedInDebug();

            TrainControlUI[] ui = new TrainControlUI[1];

            SwingUtilities.invokeAndWait(() -> ui[0] = new TrainControlUI());

            ui[0].setViewListener(model, new CountDownLatch(1));

            FrozenEditor out = new FrozenEditor(sandbox, model, ui[0]);

            if (out.asOpened == null) throw new SkipException("no autonomy configuration, so there is no editor to open");

            return out;
        }
        catch (Exception | Error failed)
        {
            // The machine's layout preference must not stay on a folder under %TEMP%
            if (sandbox != null) sandbox.close();

            throw failed;
        }
    }

    /** The setup put back as it opened, and the railway built from it with every train where the setup has it. */
    public void putBack()
    {
        putBack(ui, session, asOpened);
    }

    /** {@link #putBack()} for a window this class did not open. */
    public static void putBack(TrainControlUI ui, AutonomySession session, org.json.JSONObject setup)
    {
        Components.onTheEventThread(() ->
        {
            session.restoreSetup(setup);

            Set<String> trains = new LinkedHashSet<>(session.placementsAutonomyWillWrite().values());

            trains.addAll(session.homesToPlace().values());

            ui.rebuildRunningLayoutFromSetup(false, trains);

            return null;
        });

        Components.settle();
    }

    /** The editor on this page. */
    public LayoutEditor editorOn(String page)
    {
        return editorOn(model, ui, session, page);
    }

    /** {@link #editorOn(String)} for a window this class did not open. */
    public static LayoutEditor editorOn(MarklinControlStation model, TrainControlUI ui, AutonomySession session, String page)
    {
        final LayoutDiagram diagram = model.getLayout(page);

        Assert.assertNotNull(diagram, "precondition: no page " + page);

        LayoutEditor built = Components.onTheEventThread(() ->
        {
            LayoutEditor editor = new LayoutEditor(diagram, 30, ui, 0);

            editor.render();
            editor.setAutonomyMode(session);

            return editor;
        });

        Components.settle();

        return built;
    }

    /** The editor's Save, pressed; then the setup read back from the files it wrote. */
    public AutonomySession saveAndReadBack(LayoutEditor editor) throws Exception
    {
        AbstractButton save = Components.withText(editor, I18n.t("layout.ui.saveChanges"));

        Assert.assertNotNull(save, "precondition: the editor has no Save button");

        StationDoors.pressAndAnswer(save, message -> null);

        AutonomySession reread = new AutonomySession(sandbox.getFolder());

        reread.open(LayoutSandbox.wiredPages(model));

        return reread;
    }

    /** Closes an editor a claim opened. */
    public static void dispose(LayoutEditor editor) throws Exception
    {
        if (editor == null) return;

        // render() posts work that can make a disposed editor displayable again, which the diagram's menus then read as
        // an open editor: dispose it again until it has stayed closed for a while
        int[] closedFor = {0};

        Components.waitFor(() ->
        {
            if (!editor.isDisplayable()) return ++closedFor[0] >= 8;

            closedFor[0] = 0;

            try
            {
                SwingUtilities.invokeAndWait(editor::dispose);
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }

            return false;
        }, 5000);
    }

    @Override
    public void close() throws Exception
    {
        try
        {
            if (ui != null) SwingUtilities.invokeAndWait(ui::dispose);

            if (model != null) model.stop();
        }
        finally
        {
            sandbox.close();
        }
    }
}
