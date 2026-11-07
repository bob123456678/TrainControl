package ui;

import java.lang.reflect.Field;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.locomotive.AddLocomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;

/**
 * A typed locomotive address is read the same way at its three doors: Add Locomotive's Add, its Check Duplicates, and the
 * Locomotive Settings window's Save.  "0x" means hex for an MFX decoder and is not a number for any other.
 */
public class testATypedAddressIsReadTheSameAtEveryDoor
{
    private static final String MFX = "P3 typed mfx";
    private static final String DCC = "P3 typed dcc";
    private static final String ABS = "P3 typed abs";
    private static final String CHECKED = "P3 typed checked";
    private static final String EDIT_MFX = "P3 typed edit mfx";
    private static final String EDIT_DCC = "P3 typed edit dcc";
    private static final String[] NAMES = {MFX, DCC, ABS, CHECKED, EDIT_MFX, EDIT_DCC};

    private static final long WAIT = 10000;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the doors are dialogs");

        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedWithWindowInDebug();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        Components.settle();

        for (String name : NAMES)
        {
            if (model.getLocByName(name) != null) model.deleteLoc(name);
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null)
            {
                for (String name : NAMES)
                {
                    if (model.getLocByName(name) != null) model.deleteLoc(name);
                }
            }

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() ->
                {
                    closing.stopDrivingTrainsFromTheWholeWindow();
                    closing.dispose();
                });
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Add Locomotive's Add: "0x" is hex for MFX and not a number for DCC, and a minus sign is dropped.
     *
     * MUTATION: have `Conversion.parseTypedAddress` read "0x" as hex whatever the decoder: the DCC address is then
     * added instead of refused.  Dropping its `mfx` test the other way (never hex) makes the MFX one a refusal.
     */
    @Test
    public void testAddTakesHexOnlyForMfx() throws Exception
    {
        assertEquals(add(MFX, "0xCE4", true), one("loc.infoAddedSuccessfully"),
            "an MFX locomotive typed as hex was not added");
        assertEquals(model.getLocByName(MFX).getAddress(), 0xCE4, "the MFX locomotive has the wrong address");
        assertEquals(model.getLocByName(MFX).getDecoderType(), Locomotive.decoderType.MFX);

        assertEquals(add(DCC, "0x1F", false), one("loc.errorAddressNotNumeric"),
            "a DCC address typed as hex was not refused as not a number");
        assertNull(model.getLocByName(DCC), "a DCC locomotive was added from a hex address");

        // The Add door alone drops the sign.
        assertEquals(add(ABS, "-1301", false), one("loc.infoAddedSuccessfully"),
            "a negative address was not added");
        assertEquals(model.getLocByName(ABS).getAddress(), 1301, "the Add door no longer drops the sign");
    }

    /**
     * Check Duplicates: the same reading, so a hex address is found in use and a DCC hex one is not a number.
     *
     * MUTATION: `Conversion.parseTypedAddress` reading "0x" as hex whatever the decoder (its `mfx &&` dropped) answers
     * "in use" for the DCC hex address instead of "not a number".
     */
    @Test
    public void testCheckDuplicatesTakesHexOnlyForMfx() throws Exception
    {
        model.newMFXLocomotive(CHECKED, 0xCF8);

        assertEquals(check("0xCF8", true), one("loc.errorAddressInUse"),
            "the MFX locomotive's address, typed as hex, was not found in use");
        assertEquals(check("3320", true), one("loc.errorAddressInUse"),
            "the MFX locomotive's address, typed as decimal, was not found in use");
        assertEquals(check("0xCFA", true), one("loc.infoAddressFree"),
            "a free address, typed as hex, was not free");
        assertEquals(check("0xCF8", false), one("loc.errorAddressNotNumeric"),
            "a DCC address typed as hex was not refused as not a number");
    }

    /**
     * The Locomotive Settings window's Save: a hex address changes an MFX locomotive's address and is refused for a DCC one.
     *
     * MUTATION: `Conversion.parseTypedAddress` reading "0x" as hex whatever the decoder (its `mfx &&` dropped) lets the
     * DCC locomotive's address change to 16.
     */
    @Test
    public void testEditTakesHexOnlyForMfx() throws Exception
    {
        Locomotive mfx = model.newMFXLocomotive(EDIT_MFX, 3330);

        assertEquals(edit(mfx, "0xD05", 0).size(), 0, "an MFX hex address raised a message");
        assertEquals(model.getLocByName(EDIT_MFX).getAddress(), 0xD05, "an MFX address typed as hex did not change");

        Locomotive dcc = model.newDCCLocomotive(EDIT_DCC, 1331);

        List<String> said = edit(dcc, "0x10", 1);

        assertEquals(said.size(), 1, "a DCC hex address raised no refusal: " + said);
        assertEquals(model.getLocByName(EDIT_DCC).getAddress(), 1331, "a DCC address typed as hex changed the locomotive");
    }

    /**
     * Types into the Add Locomotive dialog and presses Add.
     */
    private static List<String> add(String name, String address, boolean mfx) throws Exception
    {
        return typeAndPress(name, address, mfx, "AddLocButton", "Add said nothing");
    }

    /**
     * Types into the Add Locomotive dialog and presses Check Duplicates.
     */
    private static List<String> check(String address, boolean mfx) throws Exception
    {
        return typeAndPress(null, address, mfx, "checkDuplicates", "Check Duplicates said nothing");
    }

    /** Types the name (unless null) and the address into Add Locomotive, presses the button, gives what was asked. */
    private static List<String> typeAndPress(String name, String address, boolean mfx, String pressed, String silent)
        throws Exception
    {
        final AddLocomotive dialog = Components.onTheEventThread(() -> new AddLocomotive(model, ui));

        try (Answering answering = Answering.start(message -> null))
        {
            Components.onTheEventThread(() ->
            {
                if (name != null) text(dialog, "LocNameInput").setText(name);

                text(dialog, "LocAddressInput").setText(address);
                radio(dialog, mfx ? "LocTypeMFX" : "LocTypeDCC").setSelected(true);
                button(dialog, pressed).doClick();

                return null;
            });

            assertTrue(answering.awaitAsked(1, WAIT), silent);

            return answering.asked();
        }
        finally
        {
            SwingUtilities.invokeAndWait(dialog::dispose);
        }
    }

    /**
     * Opens the Locomotive Settings window on a locomotive, types the address and presses Save; gives what was said,
     * after waiting for {@code expected} messages.
     */
    private static List<String> edit(Locomotive loc, String address, int expected) throws Exception
    {
        assertTrue(support.SettingsWindow.open(ui, loc, org.traincontrol.gui.LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null),
            "the door did not open the Locomotive Settings window");

        support.SettingsWindow.type("settings.address", address);

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.press(support.SettingsWindow.save());

            if (expected > 0) assertTrue(answering.awaitAsked(expected, WAIT), "Save said nothing");

            Components.settle();

            List<String> said = new java.util.ArrayList<>(answering.asked());

            support.SettingsWindow.closeIfOpen();

            return said;
        }
    }

    private static List<String> one(String key)
    {
        return java.util.Collections.singletonList(I18n.t(key));
    }

    private static JTextField text(java.awt.Component owner, String field)
    {
        return (JTextField) fieldOf(owner, field);
    }

    private static JRadioButton radio(java.awt.Component owner, String field)
    {
        return (JRadioButton) fieldOf(owner, field);
    }

    private static JButton button(java.awt.Component owner, String field)
    {
        return (JButton) fieldOf(owner, field);
    }

    private static Object fieldOf(Object owner, String name)
    {
        try
        {
            Field f = owner.getClass().getDeclaredField(name);

            f.setAccessible(true);

            return f.get(owner);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
