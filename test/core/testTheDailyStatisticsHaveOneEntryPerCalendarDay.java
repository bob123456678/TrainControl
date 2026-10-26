package core;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * The statistics window's day-by-day figures: one entry for each calendar day, newest first, with that day's figure.
 */
public class testTheDailyStatisticsHaveOneEntryPerCalendarDay
{
    private static final String NAME = "Daily stats loc";

    /** Thirty days ending on the 20th of March 2026, which holds the start of daylight saving in New York (the 8th). */
    private static final LocalDate LAST = LocalDate.of(2026, 3, 20);
    private static final int DAYS = 30;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static MarklinLocomotive loc;
    private static TimeZone zone;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedInDebug();

        // A zone with daylight saving: its start in March is a 23-hour day.
        zone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

        if (model.getLocByName(NAME) != null) model.deleteLoc(NAME);

        loc = model.newDCCLocomotive(NAME, firstFreeAddress());

        // Runtime on the day of the change and the day after it.
        loc.getHistoricalOperatingTime().put(key(LocalDate.of(2026, 3, 8)), 7000L);
        loc.getHistoricalOperatingTime().put(key(LocalDate.of(2026, 3, 9)), 11000L);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (zone != null) TimeZone.setDefault(zone);

            if (model != null)
            {
                if (model.getLocByName(NAME) != null) model.deleteLoc(NAME);

                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Thirty days across the start of daylight saving: every calendar day is there once, with the runtime of the
     * locomotives on it.
     *
     * MUTATION: step the walk in `MarklinControlStation.dailyStats` back by a fixed 86400 seconds from the local
     * midnight (a `ZonedDateTime` moved with `minusSeconds(86400)`) instead of a calendar day; the day of the change
     * is skipped.
     */
    @Test
    public void testRuntimeHasOneEntryForEachDay()
    {
        TreeMap<String, Long> stats = model.getDailyRuntimeStats(DAYS, offset());

        assertEquals(new ArrayList<>(stats.keySet()), expectedDays(),
            "the runtime figures are not one for each calendar day, newest first, from the offset");

        for (Map.Entry<String, Long> entry : stats.entrySet())
        {
            assertEquals((long) entry.getValue(), sumOfRuntimeOn(entry.getKey()),
                "the runtime on " + entry.getKey() + " is not what the locomotives ran that day");
        }

        assertTrue(stats.get("2026-03-08") >= 7000L, "the day of the change is missing the runtime on it");
    }

    /**
     * The same days for the count of locomotives that ran on each.
     *
     * MUTATION: as for the runtime figures, which share the walk.
     */
    @Test
    public void testCountHasOneEntryForEachDay()
    {
        TreeMap<String, Integer> stats = model.getDailyCountStats(DAYS, offset());

        assertEquals(new ArrayList<>(stats.keySet()), expectedDays(),
            "the counts are not one for each calendar day, newest first, from the offset");

        for (Map.Entry<String, Integer> entry : stats.entrySet())
        {
            int ran = 0;

            for (Locomotive l : model.getLocomotives())
            {
                if (l.getRuntimeOnDay(entry.getKey()) > 0) ran++;
            }

            assertEquals((int) entry.getValue(), ran, "the count on " + entry.getKey() + " is not how many ran that day");
        }

        assertTrue(stats.get("2026-03-08") >= 1 && stats.get("2026-03-09") >= 1,
            "the days either side of the change did not count the locomotive that ran on them");
    }

    private static long offset()
    {
        return LocalDate.now().toEpochDay() - LAST.toEpochDay();
    }

    private static List<String> expectedDays()
    {
        List<String> out = new ArrayList<>();

        for (int i = 0; i < DAYS; i++) out.add(key(LAST.minusDays(i)));

        return out;
    }

    private static long sumOfRuntimeOn(String key)
    {
        long sum = 0;

        for (Locomotive l : model.getLocomotives()) sum += l.getRuntimeOnDay(key);

        return sum;
    }

    private static String key(LocalDate day)
    {
        return day.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private static int firstFreeAddress()
    {
        for (int address = 1; address <= Locomotive.DCC_MAX_ADDR; address++)
        {
            if (!model.getLocAddresses().containsKey(address)) return address;
        }

        throw new IllegalStateException("every DCC address is taken");
    }
}
