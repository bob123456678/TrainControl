package core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Copy Customizations carries the custom ICONS, and Cancel puts the old ones back (MT-466).  The rule is
 * `Locomotive.Customizations`, captured and applied as one value, so the copy and the Cancel that undoes it cannot
 * disagree about what a customization is.  Also what `copyCustomizationsFrom`, a public API method, leaves alone, and
 * what it does between locomotives with different numbers of functions.
 *
 * MUTATION: take the `setLocalFunctionImageURLs` line out of `applyCustomizations` and the first two
 * claims go red - the copy leaves the target with no icon, and Cancel leaves it with the source's.
 *
 * @author Adam
 */
public class testACopiedCustomizationBringsTheIcons
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    /** Locomotives of this class's own: a real one's icons are Adam's and are not ours to move. */
    private static Locomotive source;

    private static Locomotive target;

    /** Thirty-two functions, where the other two have five. */
    private static Locomotive mfx;

    private static final String THE_SOURCES_ICON = "file:///C:/icons/f0-source.png";

    private static final String THE_TARGETS_OWN_ICON = "file:///C:/icons/other-target.png";

    /** Not F0, which the copy writes, and inside the function count of an MM2 locomotive. */
    private static final int THE_TARGETS_OWN_FUNCTION = 3;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();

        source = model.newMM2Locomotive("MT466 source", 71);
        target = model.newMM2Locomotive("MT466 target", 72);
        mfx = model.newMFXLocomotive("MT466 MFX", 75);

        assertNotNull(source, "the source locomotive was not created");
        assertNotNull(target, "the target locomotive was not created");
        assertNotNull(mfx, "the MFX locomotive was not created");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null)
            {
                if (source != null) model.deleteLoc(source.getName());

                if (target != null) model.deleteLoc(target.getName());

                if (mfx != null) model.deleteLoc(mfx.getName());

                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** All three plain, so no claim reads what another left behind. */
    @BeforeMethod
    public void plain()
    {
        for (Locomotive l : new Locomotive[] {source, target, mfx})
        {
            l.setFunctionTypes(new int[l.getNumF()], new int[l.getNumF()]);
            l.unsetLocalFunctionImageURLs();
            l.setCustomFunctions(false);
        }
    }

    /** Sets the two locomotives up as Adam had them: one custom icon on the source, one on the target. */
    private static void asHeHadThem()
    {
        int[] plain = new int[source.getNumF()];
        int[] triggers = new int[source.getNumF()];

        source.setFunctionTypes(plain, triggers);
        target.setFunctionTypes(plain, triggers);

        source.unsetLocalFunctionImageURLs();
        target.unsetLocalFunctionImageURLs();

        // The source: one customized function type, and an icon on F0 - his case exactly.
        source.setFunctionType(0, 32, 0);
        source.setLocalFunctionImageURL(0, THE_SOURCES_ICON);
        source.setCustomFunctions(true);

        // The target: an icon of its own on ANOTHER function, so a Cancel that restores nothing and one that restores
        // the source's map read differently.  The function number is checked rather than chosen:
        // `setLocalFunctionImageURL` refuses anything at or above `numF` and says so only in its return value.
        assertTrue(target.setLocalFunctionImageURL(THE_TARGETS_OWN_FUNCTION, THE_TARGETS_OWN_ICON),
            "precondition: F" + THE_TARGETS_OWN_FUNCTION + " is beyond this locomotive's "
            + target.getNumF() + " functions, so nothing was stored and this fixture says nothing");

        target.setCustomFunctions(false);
    }

    /** The copy brings the source's icon. */
    @Test
    public void testTheCopyBringsTheIcon()
    {
        asHeHadThem();

        target.copyCustomizationsFrom(source);

        assertEquals(target.getLocalFunctionImageURL(0), THE_SOURCES_ICON,
            "Copy Customizations did not bring the source's icon on F0, which is what Adam reported:"
            + " the types were written and the one custom icon was not, so every button's picture"
            + " changed and the only real customization did not arrive");

        assertEquals(target.getFunctionTypes()[0], 32,
            "precondition: the function type did not copy either, so this is not the case it names");

        assertTrue(target.isCustomFunctions(),
            "the target does not count as customized after taking somebody else's functions");

        // AND THE TARGET'S OWN ICON IS GONE, because a copy is a copy: it took the source's set.
        assertNull(target.getLocalFunctionImageURL(THE_TARGETS_OWN_FUNCTION),
            "the target kept an icon the source does not have, so the copy merged two sets rather"
            + " than copying one - and the panel would show an icon no locomotive was copied from");
    }

    /** Cancel puts the target's own icons back, not the source's. */
    @Test
    public void testCancelPutsTheOldIconsBack()
    {
        asHeHadThem();

        // WHAT IT SAID BEFORE, not what the fixture asked for: `isCustomFunctions` is derived, so the answer has to be
        // read rather than assumed.
        boolean customBefore = target.isCustomFunctions();

        Locomotive.Customizations was = target.captureCustomizations();

        target.copyCustomizationsFrom(source);

        assertEquals(target.getLocalFunctionImageURL(0), THE_SOURCES_ICON,
            "precondition: the copy did not happen, so there is nothing to undo");

        target.applyCustomizations(was);

        assertNull(target.getLocalFunctionImageURL(0),
            "after Cancel the target still carries the icon the copy put on F0");

        assertEquals(target.getLocalFunctionImageURL(THE_TARGETS_OWN_FUNCTION), THE_TARGETS_OWN_ICON,
            "Cancel did not put the target's own icon back, so pressing Copy and then Cancel loses"
            + " customizations that were there before the dialog was opened");

        assertEquals(target.getFunctionTypes()[0], 0, "Cancel left the copied function type in place");

        assertEquals(target.isCustomFunctions(), customBefore,
            "Cancel left the target reading " + target.isCustomFunctions() + " for customized where it"
            + " read " + customBefore + " before the copy");
    }

    /**
     * Copy Customizations and Cancel leave the locomotive's own customized flag as it was, and saving it stores that
     * flag, not the answer its icons give (OB-245).
     *
     * MUTATION: snapshot or save the derived answer again, and this fails.
     */
    @Test
    public void testACancelledCopyLeavesTheFlagAsItWas()
    {
        asHeHadThem();

        assertTrue(target.isCustomFunctions(), "precondition: the target's own icon does not make it read as customized");

        Locomotive.Customizations was = target.captureCustomizations();

        // As the dialog's Copy does: the source's customizations, and the flag, since the target now carries somebody
        // else's functions
        target.copyCustomizationsFrom(source);
        target.setCustomFunctions(true);

        target.applyCustomizations(was);

        // And the icon that made it read as customized deleted later
        target.unsetLocalFunctionImageURLs();

        assertFalse(target.isCustomFunctions(), "after a cancelled copy, the locomotive reads as customized with no"
            + " custom function and no icon - the copy's flag stayed, and the Central Station sync now leaves its"
            + " function types alone for good (OB-245)");
    }

    /**
     * Saving a locomotive customized by an icon alone stores its flag as it is, not the answer its icon gives (OB-245).
     *
     * MUTATION: save `isCustomFunctions` again, and this fails.
     */
    @Test
    public void testTheSaveKeepsTheFlagNotTheIconsAnswer()
    {
        asHeHadThem();

        assertTrue(target.isCustomFunctions(), "precondition: the target's own icon does not make it read as customized");

        org.traincontrol.marklin.MarklinSimpleComponent saved =
            new org.traincontrol.marklin.MarklinSimpleComponent((org.traincontrol.marklin.MarklinLocomotive) target);

        assertFalse(saved.getCustomFunctions(), "a locomotive customized by one icon is saved as customized in its own"
            + " right, so once the icon is deleted after the next start it reads as customized for ever (OB-245)");
    }

    /**
     * The two locomotives do not end up sharing one map: `setLocalFunctionImageURLs` keeps the reference it is given,
     * so a copy that passed the source's own map would make later edits on one show on the other, and a snapshot
     * holding it would restore the state it was taken to undo.
     */
    @Test
    public void testTheyDoNotShareTheMap()
    {
        asHeHadThem();

        Locomotive.Customizations was = target.captureCustomizations();

        // THE SNAPSHOT'S CLAIM IS MADE FIRST, BEFORE ANYTHING REPLACES THE MAP: `copyCustomizationsFrom` gives the
        // target a fresh map, so an icon written after the copy lands where the snapshot could never have held it.
        // Written into the map the snapshot was taken FROM, this fails exactly when the capture hands back a live
        // reference.
        assertTrue(target.setLocalFunctionImageURL(2, "file:///C:/icons/added-to-the-target.png"),
            "precondition: F2 is within this locomotive's functions, so the write below really happens");

        Map<Integer, String> snapshot = new HashMap<>(was.getIcons());

        assertFalse(snapshot.containsKey(2),
            "an icon added to the target after the snapshot was taken is in the snapshot, so the"
            + " snapshot is the locomotive's own live map and Cancel would restore the state it is"
            + " meant to undo");

        target.copyCustomizationsFrom(source);

        assertTrue(source.setLocalFunctionImageURL(1, "file:///C:/icons/added-after-the-copy.png"),
            "precondition: F1 is within this locomotive's functions");

        assertNull(target.getLocalFunctionImageURL(1),
            "an icon added to the SOURCE after the copy turned up on the target, so the two share one"
            + " map: `setLocalFunctionImageURLs` keeps the reference it is handed");
    }

    /** F1 customized on all three counts: a type, a behavior and an icon, and the flag. */
    private static void customizeF1(Locomotive l, String icon)
    {
        l.setFunctionType(1, 32, 1);
        l.setLocalFunctionImageURL(1, icon);
        l.setCustomFunctions(true);
    }

    private static void assertKeepsItsCount(Locomotive l, int count)
    {
        assertEquals(l.getNumF(), count, "the copy changed how many functions the target has");
        assertEquals(l.getFunctionTypes().length, count, "the target's types don't match its functions");
        assertEquals(l.getFunctionTriggerTypes().length, count, "the target's behaviors don't match its functions");
    }

    /**
     * A null source changes nothing.
     *
     * MUTATION: take out the null check, and this throws.
     */
    @Test
    public void testNoSourceChangesNothing()
    {
        customizeF1(target, THE_TARGETS_OWN_ICON);

        target.copyCustomizationsFrom(null);

        assertEquals(target.getFunctionTypes()[1], 32, "copying from nothing changed a function type");
        assertEquals(target.getLocalFunctionImageURL(1), THE_TARGETS_OWN_ICON, "copying from nothing changed an icon");
        assertTrue(target.hasCustomFunctionsFlag(), "copying from nothing changed the customized flag");
    }

    /**
     * Types and behaviors both arrive, each for its own function.
     *
     * MUTATION: pass `was.types` for both arguments of `setFunctionTypes` in `applyCustomizations`, and this fails.
     */
    @Test
    public void testTypesAndBehaviorsArrive()
    {
        source.setFunctionType(2, 32, 0);
        source.setFunctionType(3, 40, 1);

        int[] triggers = source.getFunctionTriggerTypes();

        target.copyCustomizationsFrom(source);

        assertEquals(target.getFunctionTypes(), source.getFunctionTypes(), "the function types did not arrive");
        assertEquals(target.getFunctionTriggerTypes(), triggers, "how each function behaves did not arrive");
    }

    /**
     * The customized flag is the source's, in both directions.
     *
     * MUTATION: drop the `setCustomFunctions` line from `applyCustomizations`, and this fails.
     */
    @Test
    public void testTheFlagIsTheSources()
    {
        source.setCustomFunctions(true);
        target.copyCustomizationsFrom(source);
        assertTrue(target.hasCustomFunctionsFlag(), "a customized source left the copy unmarked");

        source.setCustomFunctions(false);
        target.copyCustomizationsFrom(source);
        assertFalse(target.hasCustomFunctionsFlag(), "an uncustomized source left the copy marked");
    }

    /**
     * Copying from a plain locomotive wipes what the target had, because it takes the source's set whole.
     *
     * MUTATION: skip `setLocalFunctionImageURLs` when the snapshot's map is empty, and this fails.
     */
    @Test
    public void testAPlainSourceWipesTheTarget()
    {
        customizeF1(target, THE_TARGETS_OWN_ICON);

        target.copyCustomizationsFrom(source);

        assertEquals(target.getFunctionTypes()[1], 0, "the target kept a function type the plain source does not have");
        assertNull(target.getLocalFunctionImageURL(1), "the target kept an icon the plain source does not have");
        assertFalse(target.isCustomFunctions(), "the target still reads as customized after a plain source");
    }

    /**
     * The source comes through unchanged, and later edits on either side stay on that side.
     *
     * MUTATION: have `captureCustomizations` hand over the live types and `setFunctionTypes` keep an array of the right
     * length, and this fails.
     */
    @Test
    public void testTheTwoAreIndependentAfterwards()
    {
        customizeF1(source, THE_SOURCES_ICON);

        target.copyCustomizationsFrom(source);

        assertEquals(source.getFunctionTypes()[1], 32, "the copy changed the source's function type");
        assertEquals(source.getLocalFunctionImageURL(1), THE_SOURCES_ICON, "the copy changed the source's icon");
        assertTrue(source.hasCustomFunctionsFlag(), "the copy changed the source's flag");

        target.setFunctionType(1, 40, 0);
        target.setLocalFunctionImageURL(1, THE_TARGETS_OWN_ICON);

        assertEquals(source.getFunctionTypes()[1], 32, "an edit on the copy reached the source's function type");
        assertEquals(source.getLocalFunctionImageURL(1), THE_SOURCES_ICON, "an edit on the copy reached the source's icon");

        source.setFunctionType(2, 50, 0);

        assertEquals(target.getFunctionTypes()[2], 0, "an edit on the source reached the copy's function type");
    }

    /**
     * A locomotive copied from itself is unchanged.
     *
     * MUTATION: have `captureCustomizations` hand over the live map and `applyCustomizations` clear the map before
     * filling it, and this fails.
     */
    @Test
    public void testCopyingFromItselfChangesNothing()
    {
        customizeF1(source, THE_SOURCES_ICON);

        int[] types = Arrays.copyOf(source.getFunctionTypes(), source.getNumF());
        int[] triggers = Arrays.copyOf(source.getFunctionTriggerTypes(), source.getNumF());

        source.copyCustomizationsFrom(source);

        assertEquals(source.getFunctionTypes(), types, "copying from itself changed the function types");
        assertEquals(source.getFunctionTriggerTypes(), triggers, "copying from itself changed the behaviors");
        assertEquals(source.getLocalFunctionImageURL(1), THE_SOURCES_ICON, "copying from itself lost the icon");
        assertTrue(source.hasCustomFunctionsFlag(), "copying from itself changed the flag");
    }

    /**
     * Only customizations cross: not the name, address, preset speed, notes, or what the target's functions are doing.
     *
     * MUTATION: copy the preset speed in `copyCustomizationsFrom`, and this fails.
     */
    @Test
    public void testNothingElseCrosses()
    {
        source.setFunctionType(1, 32, 0);
        source.setPreferredSpeed(40);
        source.setNotes("the source's notes");
        source.setF(1, true);

        target.setPreferredSpeed(70);
        target.setNotes("the target's notes");
        target.setF(1, false);

        String name = target.getName();
        int address = target.getAddress();
        String notes = target.getNotes();

        target.copyCustomizationsFrom(source);

        assertEquals(target.getName(), name, "the copy changed the target's name");
        assertEquals(target.getAddress(), address, "the copy changed the target's address");
        assertEquals(target.getPreferredSpeed(), 70, "the copy changed the target's preset speed");
        assertEquals(target.getNotes(), notes, "the copy changed the target's notes");
        assertFalse(target.getF(1), "the copy switched on a function the target had off");
    }

    /**
     * From a locomotive with more functions, the target keeps its own count and takes the ones that fit.
     *
     * MUTATION: have `setFunctionTypes` keep the array's own length, and this fails.
     */
    @Test
    public void testAFewerFunctionsTargetTakesWhatFits()
    {
        mfx.setFunctionType(4, 32, 0);
        mfx.setFunctionType(20, 40, 0);

        target.copyCustomizationsFrom(mfx);

        assertKeepsItsCount(target, 5);
        assertEquals(target.getFunctionTypes()[4], 32, "the function that fits did not arrive");
    }

    /**
     * From a locomotive with fewer functions, the target's extra functions go plain and it still has all of them.
     *
     * MUTATION: have `applyCustomizations` write only the source's count of types over the existing ones, and this fails.
     */
    @Test
    public void testAMoreFunctionsTargetGoesPlainPastTheSource()
    {
        mfx.setFunctionType(20, 40, 0);
        source.setFunctionType(4, 32, 0);

        mfx.copyCustomizationsFrom(source);

        assertKeepsItsCount(mfx, 32);
        assertEquals(mfx.getFunctionTypes()[4], 32, "the function the source has did not arrive");
        assertEquals(mfx.getFunctionTypes()[20], 0, "a function past the source's count kept what it had");
    }

    /**
     * An icon on a function the target doesn't have doesn't arrive, so it can't mark the target as customized with
     * nothing to show for it, and can't be saved as if it belonged.
     *
     * MUTATION: take the `removeIf` line out of `copyCustomizationsFrom`, and this fails.
     */
    @Test
    public void testAnIconPastTheTargetsFunctionsDoesNotArrive()
    {
        assertTrue(mfx.setLocalFunctionImageURL(20, THE_SOURCES_ICON), "precondition: F20 is within an MFX locomotive's functions");

        target.copyCustomizationsFrom(mfx);

        assertTrue(target.getLocalFunctionImageURLs().isEmpty(), "the target holds an icon for F20 and has five functions: "
            + target.getLocalFunctionImageURLs());

        assertFalse(target.isCustomFunctions(), "the target reads as customized because of an icon it can never show");
    }
}
