package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A Return Home plan never sends a train over a tail an earlier move of the same plan left behind (OB-228).
 *
 * Adam, on MT-335, 2026-09-15: *"The 335 park works, but I get: Could not run EN57-203 from BottomInner (northbound) to
 * TopMainR0Park - the path stayed blocked."*  His log: the plan parked 75 407 DB at BottomMainA first, then routed
 * EN57-203 over BottomMainAPre -> RampDown, and the runtime refused it - *"75 407 DB is standing across BottomMainAPre ->
 * RampDown"*.  The planner modelled only the tails of trains that had not moved yet; a train it has moved has a known
 * road, the route the plan gave it, and its tail lies back along that route.
 *
 * **The shape, on sensors of its own.**  Both trains' routes home share T -> P.  Train A comes from Y and parks at A,
 * three units long behind a two-unit approach, so its tail lies back over T -> P.  Train B comes from X and goes on past
 * P to its home H.  Moved in that order, B runs into A's tail; moved the other way round, both get home.
 *
 * **Asked the way the railway would.**  Each move of the plan is driven onto the layout as a run leaves a train - the
 * locomotive moved, the side it came in by and the road it came along recorded - and before each move the edges the
 * trains already standing cover are asked of `Layout.edgesCoveredByStandingTrains`, the runtime's own walk.
 *
 * @author Adam
 */
public class testReturnHomeKeepsClearOfTheTailsItLeaves
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Locomotive trainA;
    private static Locomotive trainB;
    private static Integer lengthA;
    private static Integer lengthB;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
        sandbox = support.LayoutSandbox.open();
        model = MarklinControlStation.init(null, true, false, false, false);

        assertTrue(model.getLocList().size() >= 2, "precondition: fewer than two locomotives to plan with");

        trainA = model.getLocByName(model.getLocList().get(0));
        trainB = model.getLocByName(model.getLocList().get(1));
        lengthA = trainA.getTrainLength();
        lengthB = trainB.getTrainLength();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (trainA != null) trainA.setTrainLength(lengthA);
            if (trainB != null) trainB.setTrainLength(lengthB);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Every move of the plan is clear of the tails of the trains the plan has already put down.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testNoMoveRunsOverATailAnEarlierMoveLeft() throws Exception
    {
        Layout layout = twoRoutesSharingARun(2440);

        trainA.setTrainLength(3);
        trainB.setTrainLength(1);

        assertTrue(layout.moveLocomotive(trainA.getName(), "RT_Y", false), "could not stand train A at RT_Y");
        assertTrue(layout.moveLocomotive(trainB.getName(), "RT_X", false), "could not stand train B at RT_X");

        layout.setHomeLocomotive("RT_A", trainA.getName());
        layout.setHomeLocomotive("RT_H", trainB.getName());

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "precondition: no plan brings both trains home, though moving B first does: " + plan.getMoves());

        List<String> run = new ArrayList<>();

        for (HomeStaging.Move move : plan.getMoves())
        {
            Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

            for (Edge leg : move.getPath())
            {
                Locomotive lyingAcross = covered.get(leg);

                // The message is built only on a failure: it names the train lying there, which is null otherwise.
                if (lyingAcross != null && !lyingAcross.equals(move.getLocomotive()))
                {
                    fail("the plan sends " + move.getLocomotive().getName() + " over " + leg + ", where " + lyingAcross.getName()
                        + " - put down by an earlier move of the same plan - is lying.  The runtime refuses it, which is"
                        + " MT-335's 'the path stayed blocked'.  Moves so far: " + run + "; the plan: " + plan.getMoves());
                }
            }

            // DRIVEN ONTO THE LAYOUT AS A RUN LEAVES A TRAIN: moved, the side it came in by, and the road it came along.
            Point end = move.getEnd();
            Edge last = move.getPath().get(move.getPath().size() - 1);

            assertTrue(layout.moveLocomotive(move.getLocomotive().getName(), end.getName(), false),
                "could not replay " + move.getLocomotive().getName() + " -> " + end.getName());

            end.setArrivedFrom(layout.entrySideOf(last, end));
            end.setArrivedAlong(move.getPath());

            run.add(move.getLocomotive().getName() + " -> " + end.getName());
        }

        assertTrue(trainA.equals(layout.getPoint("RT_A").getCurrentLocomotive())
            && trainB.equals(layout.getPoint("RT_H").getCurrentLocomotive()),
            "precondition: replaying the plan did not bring both trains home: " + run);
    }

    /**
     * Y -> T and X -> T; T -> P; P -> A (the platform, entered from the west) and P -> H.  Every edge measured.
     *
     * @param s88 the first of six sensor numbers
     * @return the graph
     */
    private static Layout twoRoutesSharingARun(int s88) throws Exception
    {
        Layout built = new Layout(model);

        built.setDefaultLocSpeed(30);

        String[] names = { "RT_Y", "RT_X", "RT_T", "RT_P", "RT_A", "RT_H" };
        boolean[] stations = { true, true, true, false, true, true };

        for (int i = 0; i < names.length; i++)
        {
            built.createPoint(names[i], stations[i], model.newFeedback(s88 + i, null).getName());
        }

        String[][] rails = { { "RT_Y", "RT_T" }, { "RT_X", "RT_T" }, { "RT_T", "RT_P" }, { "RT_P", "RT_A" },
            { "RT_P", "RT_H" } };

        int[] lengths = { 1, 1, 1, 2, 1 };

        for (int i = 0; i < rails.length; i++)
        {
            built.createEdge(rails[i][0], rails[i][1]);
            built.getEdge(rails[i][0], rails[i][1]).setLength(lengths[i]);
        }

        built.getEdge("RT_P", "RT_A").setEntrySide("W");
        built.getEdge("RT_P", "RT_H").setEntrySide("W");

        return built;
    }
}
