package org.traincontrol.marklin;

import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;

/**
 * `MarklinLocomotiveCsv`'s rules that need no station.  Its integration tests are `core.testLocomotiveCsvImport` and
 * `regression.testLocomotiveCsvImportDoor`.
 */
public class testMarklinLocomotiveCsv
{
    /**
     * Replace's `clear` leaves a locomotive's own settings as a new one has them, so the merge after it takes every
     * detail from the file (FR-249); its running time stays.
     *
     * MUTATION: in `MarklinLocomotiveCsv.clear`, leave out `setFunctionTypes` or `setReversible`, and this fails.
     */
    @Test
    public void testClearLeavesTheSettingsOfANewLocomotiveAndTheRunningTime()
    {
        Locomotive loc = new MarklinLocomotive(null, 5, Locomotive.decoderType.MFX, "Unit");
        Locomotive fresh = new MarklinLocomotive(null, 5, Locomotive.decoderType.MFX, "Fresh");

        loc.setFunctionType(3, 7, 1);
        loc.setLocalFunctionImageURL(3, "icon.png");
        loc.setPreferredFunctions(new boolean[] {true, true});
        loc.setDepartureFunc(2);
        loc.setArrivalFunc(1);
        loc.setReversible(true);
        loc.setTrainLength(5);
        loc.setLocalImageURL("picture.png");
        loc.setPreferredSpeed(40);
        loc.setStructuredNotes(1900, 1950, "DR", "notes");
        loc.raiseTotalRuntimeTo(3600000L, "2026-01-02");
        MarklinLocomotiveCsv.clear(loc);

        assertEquals(settings(loc), settings(fresh), "clear did not leave the settings of a new locomotive");
        assertEquals(loc.getTotalRuntime(), 3600000L, "clear lost the running time");
    }

    /** Every setting `clear` empties, as one line. */
    private static String settings(Locomotive l)
    {
        return java.util.Arrays.toString(l.getFunctionTypes()) + java.util.Arrays.toString(l.getFunctionTriggerTypes())
            + l.isCustomFunctions() + l.getLocalFunctionImageURLs() + java.util.Arrays.toString(l.getPreferredFunctions())
            + l.getDepartureFunc() + l.getArrivalFunc() + l.isReversible() + l.getTrainLength() + l.getLocalImageURL()
            + l.getPreferredSpeed() + l.getStructuredNotes().toJson();
    }
}
