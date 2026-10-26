package support;

/**
 * The control station address the tests point at a local server, reached past the front of the class.
 * `MarklinControlStation.TEST_CS2_ADDRESS` is private, so no application code can redirect what the application says to
 * a station; this is the one place that opens it, by reflection through a constant on the class so that renaming it
 * breaks the build rather than these tests at run time.
 */
public final class TestStationAddress
{
    private TestStationAddress()
    {
    }

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

    /**
     * An address a connection to fails at once, before anything is sent: the limited broadcast address.
     *
     * With no address set, an init syncs from the application's own, 127.0.0.1 here, and Windows refuses a local
     * connection only after its retries - so both of the sync's fetches waited out CS2File.CONNECT_TIMEOUT_MS, four
     * seconds an init, for a sync that fails either way (FR-136).
     */
    public static final String NOWHERE = "255.255.255.255:1";

    /**
     * Runs an init with the address at NOWHERE, unless a test has set one of its own, and puts it back.  Only the init's
     * sync goes there: a later sync reads the application's own address, as before.
     */
    public static <T> T withNoStation(java.util.concurrent.Callable<T> work) throws Exception
    {
        boolean ours = get() == null;

        if (ours) set(NOWHERE);

        try
        {
            return work.call();
        }
        finally
        {
            if (ours) set(null);
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
