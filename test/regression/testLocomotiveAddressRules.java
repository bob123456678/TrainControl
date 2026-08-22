package regression;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.base.Locomotive.decoderType;

/**
 * What counts as a valid locomotive address, asked once and answered in one place.
 *
 * `MarklinLocomotive.validateNewAddress` is the rule. It was also written out again, differently, in
 * the Add Locomotive dialog: three separate `if` blocks testing the UPPER bound only, with no branch
 * for MULTI_UNIT, and an `abs()` above them. So **address 0 passed the dialog** - zero is not greater
 * than any maximum - and made a locomotive the model itself would have refused.
 *
 * These tests pin the rule rather than the dialog, because the dialog now asks the rule. The pair that
 * matters is the lower bound: the drifted copy would pass every upper-bound test in this file.
 */
public class testLocomotiveAddressRules
{
    /**
     * Zero is not an address, for any decoder.
     *
     * This is the clause the dialog's copy left out, and the only one that was reachable: the maximum
     * is hard to type by accident and zero is not.
     */
    @Test
    public void testZeroIsRefusedForEveryDecoder()
    {
        for (decoderType type : decoderType.values())
        {
            assertFalse(MarklinLocomotive.validateNewAddress(type, 0),
                type + " accepted address 0, which addresses no decoder at all");
        }
    }

    /**
     * And neither is a negative one.
     *
     * The dialog takes an absolute value before it looks, so this is about the rule rather than about
     * that screen - but a rule that accepts -1 accepts it from every other caller too.
     */
    @Test
    public void testNegativeIsRefusedForEveryDecoder()
    {
        for (decoderType type : decoderType.values())
        {
            assertFalse(MarklinLocomotive.validateNewAddress(type, -1),
                type + " accepted a negative address");
        }
    }

    /**
     * Each decoder's ceiling is its own, and one past it is refused.
     */
    @Test
    public void testEachDecoderKeepsItsOwnCeiling()
    {
        assertTrue(MarklinLocomotive.validateNewAddress(decoderType.MM2, Locomotive.MM2_MAX_ADDR),
            "MM2 refused its own maximum");

        assertFalse(MarklinLocomotive.validateNewAddress(decoderType.MM2, Locomotive.MM2_MAX_ADDR + 1),
            "MM2 accepted one past its maximum");

        assertTrue(MarklinLocomotive.validateNewAddress(decoderType.MFX, Locomotive.MFX_MAX_ADDR),
            "MFX refused its own maximum");

        assertFalse(MarklinLocomotive.validateNewAddress(decoderType.MFX, Locomotive.MFX_MAX_ADDR + 1),
            "MFX accepted one past its maximum");

        assertTrue(MarklinLocomotive.validateNewAddress(decoderType.DCC, Locomotive.DCC_MAX_ADDR),
            "DCC refused its own maximum");

        assertFalse(MarklinLocomotive.validateNewAddress(decoderType.DCC, Locomotive.DCC_MAX_ADDR + 1),
            "DCC accepted one past its maximum");
    }

    /**
     * Every decoder type has an answer, including any added later.
     *
     * `validateNewAddress` returns false in its default branch, so a new decoder type is refused
     * everywhere rather than let through - which is the safe way round, but means a type added and not
     * wired here would silently refuse every address. One is as much a defect as the other, and this
     * says which one is happening.
     */
    @Test
    public void testEveryDecoderTypeHasAWorkingRange()
    {
        for (decoderType type : decoderType.values())
        {
            assertTrue(MarklinLocomotive.validateNewAddress(type, 1),
                type + " refuses address 1, so either it has no branch in validateNewAddress or its "
                + "maximum is below one - a locomotive of this type cannot be created at all");
        }
    }
}
