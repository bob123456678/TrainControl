package core;

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
import org.testng.annotations.Test;

/**
 * Copy Customizations carries the custom ICONS, and Cancel puts the old ones back (MT-466).
 *
 * Adam, 2026-09-21, having copied from a locomotive whose F0 carried an icon: *"all icons are cleared
 * (source locomotive only had f0 with an icon), but the one customized icon on f0 is not loaded on the
 * target loc."*
 *
 * **Both halves of that come from one omission.**  The copy wrote the source's function TYPES, its
 * triggers and its custom flag, and never read the local image URLs - so the one thing he wanted
 * carried was not, while the type change altered which standard icon every other button drew, which is
 * the "all icons are cleared" half.
 *
 * The rule is `Locomotive.Customizations` now, captured and applied as one value, so the copy and the
 * Cancel that undoes it cannot come to disagree about what a customization is.  They were three
 * parallel fields and three assignments, and the icons were in neither list.
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

    private static final String THE_SOURCES_ICON = "file:///C:/icons/f0-source.png";

    private static final String THE_TARGETS_OWN_ICON = "file:///C:/icons/other-target.png";

    /** Not F0, which the copy writes, and inside the function count of an MM2 locomotive. */
    private static final int THE_TARGETS_OWN_FUNCTION = 3;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, false);

        source = model.newMM2Locomotive("MT466 source", 71);
        target = model.newMM2Locomotive("MT466 target", 72);

        assertNotNull(source, "the source locomotive was not created");
        assertNotNull(target, "the target locomotive was not created");
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

                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Sets the two locomotives up as Adam had them: one custom icon on the source, one on the target.
     */
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

        // The target: an icon of its own on ANOTHER function, so a Cancel that restores nothing and a
        // Cancel that restores the source's map read differently.
        //
        // THE FUNCTION NUMBER IS CHECKED RATHER THAN CHOSEN (the first draft used F8).
        // `setLocalFunctionImageURL` refuses anything at or above `numF` and says so in its return
        // value, which nobody reads - so on a locomotive with fewer functions the icon was never
        // stored and the claim below failed against code that was right.
        assertTrue(target.setLocalFunctionImageURL(THE_TARGETS_OWN_FUNCTION, THE_TARGETS_OWN_ICON),
            "precondition: F" + THE_TARGETS_OWN_FUNCTION + " is beyond this locomotive's "
            + target.getNumF() + " functions, so nothing was stored and this fixture says nothing");

        target.setCustomFunctions(false);
    }

    /**
     * The copy brings the source's icon.
     */
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

    /**
     * Cancel puts the target's own icons back, not the source's.
     */
    @Test
    public void testCancelPutsTheOldIconsBack()
    {
        asHeHadThem();

        // WHAT IT SAID BEFORE, not what the fixture asked for: `isCustomFunctions` is derived, and a
        // locomotive with any custom icon reads as customized whatever the flag was set to.  The claim
        // is that Cancel puts the answer BACK, so the answer has to be read rather than assumed.
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
     * The two locomotives do not end up sharing one map.
     *
     * `setLocalFunctionImageURLs` keeps the reference it is given, so a copy that passed the source's
     * own map would make every later edit on one show up on the other - and a snapshot holding it
     * would restore the state it was taken to undo.
     */
    @Test
    public void testTheyDoNotShareTheMap()
    {
        asHeHadThem();

        Locomotive.Customizations was = target.captureCustomizations();

        // THE SNAPSHOT'S CLAIM IS MADE FIRST, BEFORE ANYTHING REPLACES THE MAP (VD12-T4).
        //
        // `copyCustomizationsFrom` gives the target a fresh map, so an icon written to the target after
        // the copy lands somewhere the snapshot could never have held whatever `captureCustomizations`
        // does - the assertion below used to sit there and could not fail.  Written into the map the
        // snapshot was taken FROM, it fails exactly when the capture hands back a live reference.
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
}
