package regression;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import support.SourceOf;

/**
 * Cancel on the Locomotive Settings window undoes a Copy Icons because the copy never reaches the locomotive before
 * Save.
 *
 * @author Adam
 */
public class testCancelUndoesACustomizationCopy
{
    /**
     * The Locomotive Settings window's Copy Icons works on the window's buffer and never writes onto the locomotive, so
     * Cancel has nothing to undo; pinning the copy's declaration keeps that so.  Read rather than driven: the
     * driven claim is `ui.testTheLocomotiveSettingsFunctions.testCopyIconsWaitsForSave`.
     *
     * MUTATION: call `loc.copyCustomizationsFrom(from)` in `LocomotiveFunctionsTab.copyFrom`, and this fails.
     */
    @Test
    public void testTheCancelBranchCallsIt() throws Exception
    {
        String source = SourceOf.declaration("private void copyFrom(Locomotive from)");

        assertTrue(source.contains("captureCustomizations()"), "the copy no longer reads the source's customizations");

        assertFalse(source.contains("loc.copyCustomizationsFrom") || source.contains("loc.applyCustomizations")
            || source.contains("loc.setFunctionType") || source.contains("loc.setLocalFunctionImageURL"),
            "the copy writes onto the locomotive before Save, so Cancel would have to undo it");
    }
}
