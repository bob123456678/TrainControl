package ui;

import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.gui.PositionAwareJFrame;
import org.traincontrol.gui.TrainControlUI;

/**
 * A window remembered maximised is maximised once it is on screen, not before (OB-328; Adam, 2026-10-09, on MT-705:
 * "When a track diagram popup is maximized, it will not appear on top of the window (or any other windows) after the
 * button is clicked ... same for the editor.  Both work when the window is not previously maximized").
 *
 * The diagram's own windows and the editor are built anew at each click and take their remembered place, size and state
 * from the window base they share, `PositionAwareJFrame` - here one of its own, given a remembered maximised state.
 */
public class testAMaximisedWindowComesForward
{
    /** A window that remembers where it was, as every diagram window does. */
    public static final class Remembering extends PositionAwareJFrame
    {
        /** Takes the remembered place, size and state, as a diagram window does before it is shown. */
        public void takeTheRememberedBounds()
        {
            loadWindowBounds();
        }
    }

    private static final String INDEX = "OB-328";

    /**
     * Remembered maximised, the window is not maximised before it is shown - which Windows answered by putting it up
     * behind every other window - and is maximised once it is on screen.
     *
     * MUTATION: maximise it as its remembered state is read, before it is shown, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testARememberedMaximisedStateWaitsForTheWindowToBeShown() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("a window needs a display");

        final Preferences prefs = TrainControlUI.getPrefs();

        final String name = Remembering.class.getSimpleName() + "_" + INDEX;

        final boolean rememberWas = prefs.getBoolean(PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, false);

        final Remembering[] frame = new Remembering[1];
        final int[] beforeShown = {-1};
        final int[] once = {-1};

        try
        {
            prefs.putBoolean(PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, true);
            prefs.putInt(name + "_x", 60);
            prefs.putInt(name + "_y", 60);
            prefs.putInt(name + "_width", 420);
            prefs.putInt(name + "_height", 300);
            prefs.putInt(name + "_state", java.awt.Frame.MAXIMIZED_BOTH);

            SwingUtilities.invokeAndWait(() ->
            {
                frame[0] = new Remembering();
                frame[0].setWindowIndex(INDEX);
                frame[0].setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
                frame[0].takeTheRememberedBounds();

                beforeShown[0] = frame[0].getExtendedState();

                frame[0].setVisible(true);
            });

            for (long end = System.currentTimeMillis() + 5000; System.currentTimeMillis() < end; )
            {
                SwingUtilities.invokeAndWait(() -> once[0] = frame[0].getExtendedState());

                if ((once[0] & java.awt.Frame.MAXIMIZED_BOTH) == java.awt.Frame.MAXIMIZED_BOTH) break;

                Thread.sleep(50);
            }

            assertTrue((beforeShown[0] & java.awt.Frame.MAXIMIZED_BOTH) == 0, "a window remembered maximised was"
                + " maximised before it was shown, which Windows answers by putting it up behind every other window"
                + " (OB-328)");

            assertTrue((once[0] & java.awt.Frame.MAXIMIZED_BOTH) == java.awt.Frame.MAXIMIZED_BOTH, "a window remembered"
                + " maximised is not maximised once it is on screen: its state is " + once[0]);
        }
        finally
        {
            if (frame[0] != null) SwingUtilities.invokeAndWait(() -> frame[0].dispose());

            for (String key : new String[] {"_x", "_y", "_width", "_height", "_state"}) prefs.remove(name + key);

            prefs.putBoolean(PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, rememberWas);
        }
    }
}
