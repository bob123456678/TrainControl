package support;

/**
 * The control station address the tests point at a local server, reached past the front of the class.
 *
 * MarklinControlStation.TEST_CS2_ADDRESS used to be public, which made the address of the control
 * station something any code in the application could redirect - and everything the application says
 * to a station goes through it.  "Only the tests set it" was a convention rather than a rule.
 *
 * It is private now, and this is the one place that opens it.  Reflection makes the reaching-in
 * explicit and keeps it in a single file rather than at sixteen call sites, and the field is named
 * through a constant on the class itself so that renaming it breaks the build rather than breaking
 * these tests at run time.
 */
public final class TestStationAddress
{
    private TestStationAddress()
    {
    }

    /**
     * @return the address the tests have set, or null when none
     */
    public static String get()
    {
        try
        {
            return (String) field().get(null);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @param address host:port to redirect to, or null to go back to the real network
     */
    public static void set(String address)
    {
        try
        {
            field().set(null, address);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static java.lang.reflect.Field field() throws ReflectiveOperationException
    {
        java.lang.reflect.Field found = org.traincontrol.marklin.MarklinControlStation.class
            .getDeclaredField(org.traincontrol.marklin.MarklinControlStation.TEST_ADDRESS_FIELD);

        found.setAccessible(true);

        return found;
    }
}
