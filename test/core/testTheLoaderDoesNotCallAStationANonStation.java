package core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;

/**
 * Loading a configuration does not call a station a non-station (GUI4-C5).
 *
 * Since `8370abb1` a train can stand on a copy of a station square that trains may not arrive at - reversed on the
 * throttle at BottomMainA, arrivals from the east barred - and the build records it there.  The loader then logged
 * *"placed on a non-station and will not be run automatically"* for it on every load: the noun GUI3-C1 took out of the
 * other two sentences, because the square IS a station and Why not Moving? says what is wrong.  It could not tell the
 * two apart, because it wrote the warning while reading the points, before the square's other copies were read.
 *
 * On the blessed build, `BottomMainA (westbound)` is such a copy (its eastbound twin is the station) and
 * `BottomMainAPre (westbound)` is a copy of a square that is no station at all - the control, still warned about.
 *
 * MUTATION: warn for every non-station copy again, and this fails.
 *
 * @author Adam
 */
public class testTheLoaderDoesNotCallAStationANonStation
{
    private static final String BARRED = "BottomMainA (westbound)";
    private static final String PLAIN = "BottomMainAPre (westbound)";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    private static final List<String> logged = Collections.synchronizedList(new ArrayList<>());
    private static Handler tap;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, false);

        tap = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (tap != null) Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(tap);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A train on the barred copy of BottomMainA is not warned about; a train on a copy of a square that is no station
     * still is.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testATrainOnABarredCopyIsNotCalledOffAStation() throws Exception
    {
        List<String> names = model.getLocList();

        if (names.size() < 2) throw new SkipException("this fixture needs two locomotives");

        String onTheBarredCopy = names.get(0);
        String onThePlainCopy = names.get(1);

        JSONObject config = new JSONObject(new String(Files.readAllBytes(Paths.get("test", "baseline",
            "configuration.json")), StandardCharsets.UTF_8));

        JSONArray points = config.getJSONArray("points");

        boolean foundBarred = false;
        boolean foundPlain = false;

        for (int i = 0; i < points.length(); i++)
        {
            JSONObject point = points.getJSONObject(i);

            point.remove("loc");

            if (BARRED.equals(point.getString("name")))
            {
                assertFalse(point.optBoolean("station", false), "precondition: " + BARRED + " is a station on the"
                    + " blessed build");

                point.put("loc", new JSONObject().put("name", onTheBarredCopy));

                foundBarred = true;
            }

            if (PLAIN.equals(point.getString("name")))
            {
                assertFalse(point.optBoolean("station", false), "precondition: " + PLAIN + " is a station on the"
                    + " blessed build");

                point.put("loc", new JSONObject().put("name", onThePlainCopy));

                foundPlain = true;
            }
        }

        assertTrue(foundBarred && foundPlain, "precondition: the blessed build has no " + BARRED + " or no " + PLAIN);

        logged.clear();

        Layout loaded = Layout.fromJSON(config.toString(), model);

        assertTrue(loaded.isValid(), "precondition: the blessed build with two trains placed does not load");

        assertTrue(loaded.isABarredCopyOfAStation(loaded.getPoint(BARRED)), "precondition: " + BARRED + " is not"
            + " a barred copy of a station once loaded");

        String barredWarning = I18n.f("autolayout.warnLocomotivePlacedOnNonStation", onTheBarredCopy);
        String plainWarning = I18n.f("autolayout.warnLocomotivePlacedOnNonStation", onThePlainCopy);

        List<String> seen = new ArrayList<>(logged);

        assertTrue(seen.contains(plainWarning), "control: a train on " + PLAIN + ", a copy of a square that is no"
            + " station, is not warned about - so the claim below would pass for nothing.  Logged: " + seen);

        assertFalse(seen.contains(barredWarning), "the loader called BottomMainA, a station, a non-station for the"
            + " train on its barred copy (GUI4-C5) - Why not Moving? already says what is wrong there");
    }
}
