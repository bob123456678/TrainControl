package regression;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * Every door that offers a destination asks the same question, and the "..." means the list was cut
 * short (Adam, 2026-09-12).
 *
 * Two reports, one cause.
 *
 * *"why can non-reversible EN57-947 be manually sent from BottomSecondary to BottomMainC (terminus,
 * train is non reversible, not a berth)?"* - because `OB-205` put that rule in
 * `Layout.isOfferableToOperator` and only the track diagram's menu asks it. The locomotive commands
 * tab lists whatever the search returns and marks what autonomy will not choose with a dash, which is
 * deliberate for a parking berth and wrong for a terminus the train cannot leave: a dash is a label,
 * not a refusal, and the row is still clickable.
 *
 * *"why is the ... below the list of options visible? shouldn't this be only shown if there are many
 * more regular destinations than can be displayed?"* - because the count behind it was taken BEFORE
 * the menu dropped anything, so it included every square the menu is never going to show. `OB-205`
 * added a whole new class of those - every ordinary terminus, whenever the train cannot reverse - so
 * for a non-reversible locomotive it fired on essentially every right-click.
 *
 * **Both are about a count or a list disagreeing with what is on screen**, which is why they are one
 * class.
 *
 * MUTATION: take `isOfferableToOperator` out of either door and the census below names it; put the
 * pre-filter count back into the ellipsis rule and the second test fails on the case where nothing was
 * truncated.
 *
 * @author Adam
 */
public class testTheDestinationDoorsAgree
{
    /**
     * Both doors ask `isOfferableToOperator` before they offer a destination.
     *
     * A source census rather than a built window, which is the shape `core.testNonReversibleTrains
     * .testEveryManualDoorHandsOverAPrompt` already uses for this question: what is being checked is
     * that a door asks the rule at all, and the rule's own answers are tested where it lives.
     *
     * @throws Exception if a door cannot be read
     */
    @Test
    public void testEveryDoorThatOffersADestinationAsksTheRule() throws Exception
    {
        final String[][] doors =
        {
            {"src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java", "the track diagram menu"},
            {"src/org/traincontrol/gui/AutoLocomotiveStatus.java", "the Locomotive commands tab"},
        };

        for (String[] door : doors)
        {
            java.io.File file = new java.io.File(door[0]);

            assertTrue(file.exists(), "precondition: " + door[0] + " has to be readable, or this test "
                + "reports every door as asking and means nothing");

            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            // COMMENTS STRIPPED, for the reason `testEveryManualDoorHandsOverAPrompt` gives one file
            // over: a guard that cannot tell an explanation from an instruction reports on prose.
            String code = source.replaceAll("(?s)/[*].*?[*]/", " ").replaceAll("//[^\\r\\n]*", " ");

            assertTrue(code.contains("isOfferableToOperator("),
                door[1] + " (" + door[0] + ") offers destinations without asking"
                + " Layout.isOfferableToOperator, so a terminus a non-reversible train could not leave"
                + " is on its list. Adam, 2026-09-12: \"why can non-reversible EN57-947 be manually"
                + " sent from BottomSecondary to BottomMainC (terminus, train is non reversible, not a"
                + " berth)?\" - OB-205 put that rule in one door of two");
        }
    }

    /**
     * The "..." appears when the list was cut short, and not merely because something was filtered.
     *
     * **Two claims, and the second is structural on purpose.** The arithmetic below is a pin: it says
     * what the rule computes. It cannot catch this defect on its own, because what was wrong was never
     * the comparison - it was WHICH NUMBER the menu handed it. `PathOptions.possible` was counted
     * before the menu dropped anything, so it included every square the menu never shows, and
     * `possible > shown` was true whenever anything at all had been filtered.
     *
     * So the second claim is that no such number exists to be passed. A count taken before the filter
     * is the defect itself, and removing it is what makes the mistake unavailable rather than merely
     * corrected - the same reason `Layout.hoveredSquare` forgets the square it hands back.
     *
     * The menu is package-private, so both are reached by reflection: a test outside its package
     * cannot name it, and building a real window to count menu items would be testing Swing.
     *
     * @throws Exception if the rule cannot be reached
     */
    @Test
    public void testTheEllipsisMeansTheListWasCutShort() throws Exception
    {
        Class<?> menu = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

        Method rule = menu.getDeclaredMethod("theListWasCutShort", int.class, int.class, int.class);

        rule.setAccessible(true);

        // Everything this menu offers is on screen - six of six, none in the submenu.
        assertFalse((Boolean) rule.invoke(null, 6, 6, 0),
            "the way out to the autonomy tab is offered when every destination this menu shows is"
            + " already on screen. Adam: \"shouldn't this be only shown if there are many more regular"
            + " destinations than can be displayed?\"");

        // The same, with some of them in More Destinations: still nothing cut short.
        assertFalse((Boolean) rule.invoke(null, 6, 4, 2),
            "the way out is offered while every destination is on screen, some of them in More"
            + " Destinations - which is the state FR-058 put them in so that they WOULD be visible");

        // AND THE CASE IT EXISTS FOR: more ordinary destinations than fitted.
        assertTrue((Boolean) rule.invoke(null, 20, 12, 0),
            "the list was cut short - 12 of 20 shown - and the menu did not offer the way to the rest,"
            + " so those destinations are unreachable from the diagram");

        assertFalse((Boolean) rule.invoke(null, 0, 0, 0),
            "a menu with nothing to offer reported that its list had been cut short");

        // AND THERE IS ONLY ONE PLACE THAT DRAWS IT.
        //
        // Adam, after the first fix: *"I still see ... for 74 407 DB at Tunnel, even though it has no
        // valid paths"*.  A second site sat outside the rule, for the case where the train has nowhere
        // at all to go - so the rule could be right and the symptom stay.  One site is what makes the
        // rule the whole answer, and counting them is the only way to say that from outside the class.
        String menuSource = new String(Files.readAllBytes(
            Paths.get("src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java")),
            StandardCharsets.UTF_8);

        String menuCode = menuSource.replaceAll("(?s)/[*].*?[*]/", " ").replaceAll("//[^\r\n]*", " ");

        // Counted with indexOf rather than a regex: the marker is punctuation and a quote, and
        // escaping that twice - once for Java, once for the pattern - is how a guard comes to
        // search for something it does not mean.
        String marker = "new JMenuItem(\"...\")";

        int sites = 0;

        for (int at = menuCode.indexOf(marker); at >= 0; at = menuCode.indexOf(marker, at + 1))
        {
            sites++;
        }

        assertEquals(sites, 1,
            "the menu draws the \"...\" in " + sites + " places. It must be decided in one, through"
            + " theListWasCutShort - a second site is outside the rule, which is how a train with no"
            + " destinations at all kept showing it after the rule was corrected");

        // AND THE NUMBER THAT CAUSED IT IS GONE.
        Class<?> options = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu$PathOptions");

        for (java.lang.reflect.Field field : options.getDeclaredFields())
        {
            assertFalse("possible".equals(field.getName()),
                "PathOptions still carries `possible`, a count of everything the search returned taken"
                + " BEFORE the menu dropped the squares it never shows. That is the number the ellipsis"
                + " was comparing against, and OB-205 added a whole class of filtered-out squares to"
                + " it - every ordinary terminus, whenever the train cannot reverse - so for a"
                + " non-reversible locomotive the \"...\" appeared on essentially every right-click."
                + " Fields: " + java.util.Arrays.toString(options.getDeclaredFields()));
        }
    }

    /**
     * Every why-window names the tier it answers for, so on Manual none of them gives autonomy's reasons (REG4, GUI3-C1).
     *
     * `Layout.explainCannotStart`, `explainDestinations` and `explainDestinationsGrouped` each have a form that takes
     * `byHand`, and the one-argument form answers for autonomy.  GUI3-C1 gave the diagram's Why not Moving? the tier
     * (Adam, OB-225: *"in manual mode, I still get reasons like ... will never be chosen in autonomy"*); the locomotive
     * list's "No available paths" tooltip and its why-window kept the one-argument form, so on Manual they still said a
     * train on a copy that is no station cannot be sent anywhere - while the list offered it routes.
     *
     * A source census, the shape the first claim here uses: what is checked is that a door says which question it asks,
     * and each tier's answers are tested where they live (`core.testWhyStuck`).  Every file under `gui/`, so a door
     * added later is counted without being listed.
     *
     * MUTATION: give either call in `AutoLocomotiveStatus` its one-argument form back, and this names it.
     *
     * @throws Exception if the sources cannot be read
     */
    @Test
    public void testEveryWhyNamesItsTier() throws Exception
    {
        java.util.List<String> untiered = new java.util.ArrayList<>();

        int calls = 0;

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(Paths.get("src/org/traincontrol/gui")))
        {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) walk::iterator)
            {
                if (!file.toString().endsWith(".java")) continue;

                String code = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    .replaceAll("(?s)/[*].*?[*]/", " ").replaceAll("//[^\\r\\n]*", " ");

                java.util.regex.Matcher call = java.util.regex.Pattern
                    .compile("\\.(explainCannotStart|explainDestinationsGrouped|explainDestinations)\\(").matcher(code);

                while (call.find())
                {
                    calls++;

                    // The arguments, to the matching parenthesis: a comma at depth one is a second argument.
                    int depth = 1;
                    boolean second = false;

                    for (int i = call.end(); i < code.length() && depth > 0; i++)
                    {
                        char c = code.charAt(i);

                        if (c == '(') depth++;
                        else if (c == ')') depth--;
                        else if (c == ',' && depth == 1) second = true;
                    }

                    if (!second) untiered.add(file.getFileName() + ": " + call.group(1));
                }
            }
        }

        assertTrue(calls >= 6, "precondition: the census found " + calls + " calls to the three explain methods under"
            + " gui/, fewer than the diagram's two and the locomotive list's four - it is not reading the doors");

        assertTrue(untiered.isEmpty(), "a why-window asks autonomy's question whatever the Path Type, so on Manual it"
            + " gives reasons that stop nothing a hand-driven send does (REG4, GUI3-C1; Adam, OB-225).  Name the tier -"
            + " `!layout.isAutoRunning()` where the door has no Path Type of its own: " + untiered);
    }
}
