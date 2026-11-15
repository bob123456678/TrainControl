package ui;

import java.util.HashMap;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.gui.TrainControlUI;
import support.Display;
import support.Reflect;
import static support.SandboxedWindow.build;

/**
 * Every reason a return home is refused has its own sentence.
 *
 * describeStagingOutcome is a switch with a `default` arm, and the default says "no return plan found" - which is the
 * right thing for exactly one outcome and the wrong thing for every other.  An outcome added without a case therefore
 * does not fail; it silently tells the operator the search ran out of room, whatever actually happened.
 *
 * READY is exempt, and only READY: describeStagingPlan is reached from behind `if
 * (!plan.isPossible())`, so a plan that exists never asks for a sentence.
 *
 * MUTATION this catches: delete any case from the switch and its outcome collides with
 * NO_PLAN_FOUND's message.
 *
 * @author Adam
 */
public class testStagingOutcomeMessages
{
    @Test
    public void testEveryRefusalHasItsOwnSentence() throws Exception
    {
        Display.needed("describeStagingOutcome is a method on the window");

        TrainControlUI ui = build();

        java.lang.reflect.Method describe = Reflect.method(TrainControlUI.class,
            "describeStagingOutcome", HomeStaging.Outcome.class, java.util.List.class);

        Map<String, HomeStaging.Outcome> seen = new HashMap<>();

        for (HomeStaging.Outcome outcome : HomeStaging.Outcome.values())
        {
            if (outcome == HomeStaging.Outcome.READY) continue;

            String message = (String) describe.invoke(ui, outcome, null);

            assertNotNull(message, outcome + " has no message at all");

            assertFalse(message.trim().isEmpty(), outcome + " has an empty message");

            assertFalse(message.startsWith("autolayout."),
                outcome + " fell through to a missing bundle key: " + message);

            HomeStaging.Outcome already = seen.put(message, outcome);

            assertNull(already,
                outcome + " and " + already + " are described by the same sentence, which means one "
                + "of them has no case in the switch and is being answered by the default: " + message);
        }

        assertEquals(seen.size(), HomeStaging.Outcome.values().length - 1,
            "one sentence per outcome, READY excepted");
    }
}
