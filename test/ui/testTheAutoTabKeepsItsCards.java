package ui;

import java.awt.Component;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomy.AutoLocomotiveStatus;
import org.traincontrol.marklin.MarklinControlStation;
import support.Components;
import support.Reflect;

/**
 * The Auto tab keeps its cards across a running-layout rebuild that leaves the same trains in the same order, and a
 * kept card shows exactly what a card built anew would (OB-460).  On the single-switch railway, in a sandbox.
 */
public class testTheAutoTabKeepsItsCards
{
    private static final String PAGE = "1 - Junction";

    private support.LayoutSandbox sandbox;
    private MarklinControlStation model;
    private TrainControlUI ui;
    private AutonomySession session;
    private Locomotive alpha, beta;

    /**
     * With nothing changed, the diagram menu's rebuild and a setup change keep every card, each now on the running
     * layout the rebuild made, saying what a new card says.
     *
     * MUTATION: in `repaintAutoLocListFull`, build every card anew (`kept = null`), and this fails.
     */
    @Test(timeOut = 120000)
    public void testARebuildWithNothingChangedKeepsTheCards() throws Exception
    {
        List<AutoLocomotiveStatus> before = cards();

        Layout was = railway();

        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());
        quiesce();

        assertNotSame(railway(), was, "precondition: the rebuild did not replace the running layout");

        assertSameCards(before, "the rebuild");
        assertEveryCardIsAsNew();

        SwingUtilities.invokeAndWait(() -> ui.autonomySetupChanged());
        quiesce();

        assertSameCards(before, "a setup change");
        assertEveryCardIsAsNew();
    }

    /**
     * Each change a card shows still reaches it through its door: a train placed, moved, turned, renamed, taken off,
     * and arriving from a run; after each, every card says what a new card says.
     *
     * MUTATION: in `AutoLocomotiveStatus.showOn`, leave out the name, and the rename fails.
     */
    @Test(timeOut = 180000)
    public void testEachChangeRedrawsItsCard() throws Exception
    {
        Locomotive gamma = train("Gamma Train");

        stand(gamma, 3, 3);
        rebuild();

        assertNotNull(card(gamma), "a train placed has no card");
        assertEveryCardIsAsNew();

        Object facing = session.facingOf(gamma.getName());

        SwingUtilities.invokeAndWait(() -> call("followTheTurn", gamma.getName()));
        quiesce();

        assertNotEquals(session.facingOf(gamma.getName()), facing, "precondition: the train did not turn");
        assertEveryCardIsAsNew();

        stand(gamma, 7, 3);
        rebuild();

        assertTrue(station(gamma).contains("MainPlatform"), "a train moved has its old station: " + station(gamma));
        assertEveryCardIsAsNew();

        renamed(alpha, "Aardvark Train");

        assertEquals(text(card(alpha), "locName"), "Aardvark Train", "a train renamed has its old name");
        assertEveryCardIsAsNew();

        SwingUtilities.invokeAndWait(() ->
        {
            session.placeLocomotive(new TileKey(PAGE, 7, 3), null);
            railway().moveLocomotive(null, railway().getLocomotiveLocation(gamma).getName(), false);
        });
        rebuild();

        assertNull(railway().getLocomotiveLocation(gamma), "precondition: the train is still on the railway");
        assertEveryCardIsAsNew();

        arrives(beta, "Approach");
        rebuild();

        assertTrue(station(beta).contains("Approach"), "a train arrived has its old station: " + station(beta));
        assertEveryCardIsAsNew();
    }

    // ---------------------------------------------------------------- the railway

    @BeforeMethod
    public void open() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the Auto tab is a window");

        // Closed again if anything here throws, which puts the layout preference back (OB-111)
        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

            model = support.TestStation.simulatedWithWindowInDebug();
            model.setNetworkCommState(false);

            ui = (TrainControlUI) model.getGUI();

            assertTrue(Components.waitFor(() -> ui.getAutonomySession() != null
                && model.getAutoLayoutIfLoaded() != null, 30000), "the window built no railway");
        }
        catch (Throwable failed)
        {
            close();

            throw failed;
        }

        session = ui.getAutonomySession();

        alpha = train("Alpha Train");
        beta = train("Beta Train");

        stand(alpha, 1, 3);
        stand(beta, 6, 2);
        rebuild();

        assertEquals(cards().size(), 2, "precondition: the two trains have no cards");
    }

    @AfterMethod(alwaysRun = true)
    public void close() throws Exception
    {
        try
        {
            if (ui != null) SwingUtilities.invokeAndWait(() -> ui.dispose());

            if (model != null) model.stop();
        }
        finally
        {
            ui = null;
            model = null;

            if (sandbox != null) sandbox.close();

            sandbox = null;
        }
    }

    private Layout railway()
    {
        return model.getAutoLayout();
    }

    private int nextAddress = 3501;

    /** A reversible train made for the claim, in memory only. */
    private Locomotive train(String name)
    {
        Locomotive made = model.newMM2Locomotive(name, nextAddress++);

        made.setReversible(true);

        return made;
    }

    /** Stands the train on the square in the setup and on the running layout, as the diagram's placement does. */
    private void stand(Locomotive train, int x, int y) throws Exception
    {
        final TileKey tile = new TileKey(PAGE, x, y);

        SwingUtilities.invokeAndWait(() ->
        {
            session.placeLocomotive(tile, train.getName());

            String copy = session.facingsFor(tile).keySet().iterator().next();

            railway().moveLocomotive(train.getName(), copy, false);
        });
    }

    /** The diagram menu's rebuild of the running layout from the setup. */
    private void rebuild() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.rebuildRunningLayoutFromSetup());
        quiesce();
    }

    /** Renames the train through the door Locomotive Settings' Save goes through. */
    private void renamed(Locomotive train, String name) throws Exception
    {
        Object actions = Reflect.get(TrainControlUI.class, ui, "locomotiveActions");

        Method write = Reflect.method(actions.getClass(), "writeNameAndAddress", Component.class, Locomotive.class,
            String.class, String.class, Locomotive.decoderType.class);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                assertTrue((Boolean) write.invoke(actions, ui, train, name, null, null), "the rename was refused");
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        quiesce();
    }

    /** Drives the train to the station on the running layout, simulated, and waits for it to arrive. */
    private void arrives(Locomotive train, String station) throws Exception
    {
        final Layout running = railway();

        running.setSimulate(true);

        List<Edge> path = null;

        for (List<Edge> each : running.getPossiblePaths(train, true))
        {
            if (each.get(each.size() - 1).getEnd().getName().startsWith(station)) path = each;
        }

        assertNotNull(path, "precondition: " + train.getName() + " has no way to " + station);

        final List<Edge> chosen = path;

        Thread driving = new Thread(() -> running.executePath(chosen, train, 30, null), "driven by the claim");

        driving.setDaemon(true);
        driving.start();

        assertTrue(Components.waitFor(() -> !driving.isAlive() && running.getLocomotiveLocation(train) != null
            && running.getLocomotiveLocation(train).getName().startsWith(station), 60000), train.getName()
            + " did not arrive at " + station);

        assertTrue(Components.waitFor(() -> station(train).contains(station), 30000), "the arrival did not reach the"
            + " card: " + station(train));
    }

    private void call(String method, String argument)
    {
        try
        {
            Reflect.method(TrainControlUI.class, method, String.class).invoke(ui, argument);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** Until the event thread and the cards' search thread have both run dry; each queues work on the other. */
    private void quiesce() throws Exception
    {
        ExecutorService renderer =
            (ExecutorService) Reflect.method(TrainControlUI.class, "autonomyRenderer").invoke(ui);

        for (int turn = 0; turn < 4; turn++)
        {
            Components.drain(1);
            renderer.submit(() -> { }).get();
        }

        Components.settle();
    }

    // ---------------------------------------------------------------- the cards

    private List<AutoLocomotiveStatus> cards()
    {
        return Components.onTheEventThread(() -> Components.all(ui.getContentPane(), AutoLocomotiveStatus.class,
            c -> true));
    }

    private AutoLocomotiveStatus card(Locomotive train)
    {
        for (AutoLocomotiveStatus card : cards()) if (card.getLocomotive() == train) return card;

        return null;
    }

    private String station(Locomotive train)
    {
        AutoLocomotiveStatus card = card(train);

        return card == null ? "(no card)" : text(card, "locStation");
    }

    private void assertSameCards(List<AutoLocomotiveStatus> before, String after)
    {
        List<AutoLocomotiveStatus> now = cards();

        assertEquals(now.size(), before.size(), "the cards after " + after);

        for (int i = 0; i < now.size(); i++)
        {
            assertTrue(now.get(i) == before.get(i), "the card of " + now.get(i).getLocomotive().getName() + " was"
                + " built again after " + after);
        }
    }

    /** Every card says what a card built now for its train says once its search is in, and is on the running layout. */
    private void assertEveryCardIsAsNew() throws Exception
    {
        for (AutoLocomotiveStatus card : cards())
        {
            Locomotive train = card.getLocomotive();

            AutoLocomotiveStatus[] fresh = new AutoLocomotiveStatus[1];

            SwingUtilities.invokeAndWait(() -> fresh[0] = new AutoLocomotiveStatus(train, ui, true));

            List<List<Edge>> found = fresh[0].findPaths();

            SwingUtilities.invokeAndWait(() -> fresh[0].updateState(train, found, found != null));

            assertEquals(Components.onTheEventThread(() -> look(card)),
                Components.onTheEventThread(() -> look(fresh[0])), "the card of " + train.getName()
                + " does not say what a new card says");
        }
    }

    /** All a card shows, and the running layout it sends on. */
    private String look(AutoLocomotiveStatus card)
    {
        StringBuilder out = new StringBuilder();

        for (String name : new String[] {"locName", "locStation", "locDest"})
        {
            JLabel label = (JLabel) field(card, name);

            out.append(name).append('=').append(label.getText()).append(' ').append(label.getForeground()).append(' ')
                .append(label.getBackground()).append(' ').append(label.getIcon() != null).append(' ')
                .append(label.getToolTipText()).append('\n');
        }

        JList<?> list = (JList<?>) field(card, "locAvailPaths");

        out.append("paths=").append(list.isVisible());

        for (int i = 0; i < list.getModel().getSize(); i++) out.append(" | ").append(list.getModel().getElementAt(i));

        JToggleButton pause = (JToggleButton) field(card, "pauseButton");

        out.append("\npause=").append(pause.isSelected()).append(' ').append(pause.isVisible());

        out.append("\non the running layout=").append(field(card, "layout") == railway());

        return out.toString();
    }

    private static String text(AutoLocomotiveStatus card, String label)
    {
        return Components.onTheEventThread(() -> ((JLabel) field(card, label)).getText());
    }

    private static Object field(AutoLocomotiveStatus card, String name)
    {
        try
        {
            return Reflect.get(AutoLocomotiveStatus.class, card, name);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
