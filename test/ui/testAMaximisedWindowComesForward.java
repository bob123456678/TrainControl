package ui;

import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.gui.widgets.PositionAwareJFrame;
import org.traincontrol.gui.TrainControlUI;

/**
 * A window remembered maximized is maximized once it is on screen, not before (OB-328; MT-705).  The diagram's own
 * windows and the editor take their remembered place, size and state from `PositionAwareJFrame` - here one of its own,
 * given a remembered maximized state.
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

        /** Remembers the window's bounds, as moving it does. */
        public void saveBounds()
        {
            saveWindowBounds();
        }
    }

    private static final String INDEX = "OB-328";

    /**
     * Remembered maximized, the window is not maximized before it is shown - which Windows answered by putting it up
     * behind every other window - and is maximized once it is on screen.
     *
     * MUTATION: maximize it as its remembered state is read, before it is shown, and this fails.
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

    /**
     * Remembering window places is on where it was never set, a stored untick is honored, and a window placed at a spot
     * comes back there with the preference unset (FR-252).
     *
     * MUTATION: read the preference with default false in `PositionAwareJFrame.remembersWindowLocations`, and this fails.
     */
    @Test
    public void testRememberingWindowPlacesIsOnUnlessUnticked() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("a window needs a display");

        final Preferences prefs = TrainControlUI.getPrefs();
        final String was = prefs.get(PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, null);
        final String name = Remembering.class.getSimpleName() + "_FR-252";
        final Remembering[] frame = new Remembering[2];
        final java.awt.Point[] back = new java.awt.Point[1];

        try
        {
            prefs.remove(PositionAwareJFrame.REMEMBER_WINDOW_LOCATION);
            assertTrue(PositionAwareJFrame.remembersWindowLocations(), "never set: the switch reads off");

            SwingUtilities.invokeAndWait(() ->
            {
                frame[0] = new Remembering();
                frame[0].setWindowIndex("FR-252");
                frame[0].setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
                frame[0].setBounds(137, 91, 400, 300);
                frame[0].setVisible(true);
                frame[0].saveBounds();
                frame[0].dispose();

                frame[1] = new Remembering();
                frame[1].setWindowIndex("FR-252");
                frame[1].takeTheRememberedBounds();
                back[0] = frame[1].getLocation();
            });

            org.testng.Assert.assertEquals(back[0], new java.awt.Point(137, 91), "a window placed at a spot does not"
                + " come back there with the preference unset");

            prefs.putBoolean(PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, false);
            assertTrue(!PositionAwareJFrame.remembersWindowLocations(), "a stored untick is not honored");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> { for (Remembering f : frame) if (f != null) f.dispose(); });

            for (String key : new String[] {"_x", "_y", "_width", "_height", "_state"}) prefs.remove(name + key);

            if (was == null) prefs.remove(PositionAwareJFrame.REMEMBER_WINDOW_LOCATION);
            else prefs.put(PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, was);
        }
    }
}
