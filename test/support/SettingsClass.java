package support;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.traincontrol.gui.TrainControlUI;

/**
 * The start and end of a test class for the Locomotive Settings window: the simulated railway with its window, and
 * the end that forgets the locomotives the class made.  Not a test.
 */
public abstract class SettingsClass
{
    protected static SettingsFixture fixture;
    protected static TrainControlUI ui;

    /** The locomotives the class makes, which the end of the class takes out of the database. */
    protected abstract String[] made();

    @BeforeClass
    public void openTheRailway() throws Exception
    {
        fixture = SettingsFixture.open();
        ui = fixture.ui;
    }

    @AfterClass(alwaysRun = true)
    public void closeTheRailway() throws Exception
    {
        SettingsFixture.tearDown(fixture, made());
    }
}
