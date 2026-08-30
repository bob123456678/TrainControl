package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;

/**
 * Find Route lands on the route the user meant, or says it cannot (FR-044).
 *
 * Adam: "addition to the route jmenu: 'Find Route' popup that asks the user for the route name.  system
 * jumps to the route page and highlights the matched cell (scrolling to it if needed), otherwise shows
 * a notice that it doesn't exist."
 *
 * The jumping, the scrolling and the highlight are for MT-218 - they need a window and an eye. What is
 * here is the half that decides WHICH route, which has no appearance at all: get it wrong and the
 * feature confidently highlights the wrong route, or refuses one whose name was typed in full.
 */
public class testFindRoute
{
    private static final List<String> ROUTES = Arrays.asList(
        "Yard", "Yard Bypass", "Main Line", "Depot Entry", "depot exit");

    /**
     * A name typed in full finds that route, whatever the case.
     */
    @Test
    public void testAnExactNameIsFound()
    {
        assertEquals(TrainControlUI.routeNamedIn(ROUTES, "Main Line"), "Main Line",
            "a route named exactly was not found");

        assertEquals(TrainControlUI.routeNamedIn(ROUTES, "main line"), "Main Line",
            "matching is case sensitive, so a route has to be typed the way it was spelt - which is "
            + "not what somebody looking for it knows");

        assertEquals(TrainControlUI.routeNamedIn(ROUTES, "DEPOT EXIT"), "depot exit",
            "the stored name's own case was not matched against");
    }

    /**
     * An exact name wins even when it is also part of another route's name.
     *
     * **This is the case the order of the two loops exists for.** "Yard" is both a route and a
     * substring of "Yard Bypass". A rule that looked for substrings first would find two matches, call
     * it ambiguous, and refuse to find a route whose name the user had typed in full - which is the
     * most confident thing a user can do and the worst thing to refuse.
     *
     * MUTATION: move the exact-match loop below the contains loop in `routeNamedIn` and this fails.
     */
    @Test
    public void testAnExactNameBeatsBeingPartOfAnother()
    {
        assertEquals(TrainControlUI.routeNamedIn(ROUTES, "Yard"), "Yard",
            "typing a route's full name found nothing, because that name is also part of another "
            + "route's name and the search treated it as ambiguous");
    }

    /**
     * A fragment that fits one route finds it; one that fits several finds none.
     *
     * Ambiguity is not resolved by guessing. Landing silently on one of two candidates is worse than
     * saying no, because the user is then looking at a route they did not ask for and nothing on
     * screen says so.
     */
    @Test
    public void testAFragmentIsFoundOnlyWhenItIsUnique()
    {
        assertEquals(TrainControlUI.routeNamedIn(ROUTES, "Bypass"), "Yard Bypass",
            "a fragment matching exactly one route did not find it");

        assertEquals(TrainControlUI.routeNamedIn(ROUTES, "Main"), "Main Line",
            "a fragment matching exactly one route did not find it");

        assertNull(TrainControlUI.routeNamedIn(ROUTES, "depot"),
            "the word 'depot' is part of two route names, so there is no single answer - Find Route "
            + "offers the choice instead, and landing on one of them silently would put the user in "
            + "front of a route they did not ask for");
    }

    /**
     * Nothing matching, and nothing to match against, both answer null rather than throwing.
     */
    @Test
    public void testNothingMatchingIsNotAnError()
    {
        assertNull(TrainControlUI.routeNamedIn(ROUTES, "Sidings"),
            "a name no route carries should come back as not found, which is what raises the notice");

        assertNull(TrainControlUI.routeNamedIn(Collections.<String>emptyList(), "Yard"),
            "searching a layout with no routes threw or matched something");

        assertNull(TrainControlUI.routeNamedIn(null, "Yard"),
            "a null list should be no match rather than an exception - the route list is rebuilt "
            + "constantly and this is called from a menu");

        assertNull(TrainControlUI.routeNamedIn(ROUTES, null),
            "a null search should be no match rather than an exception");
    }

    /**
     * Several candidates come back as several, so the user can be asked (MT-218).
     *
     * Adam: "let's support partial matching if there is no exact match as entered." Partial matching
     * was already there; what it did when a fragment fitted more than one route was give up. Refusing
     * is not the only alternative to guessing - the third option is to put the list in front of the
     * user, and that needs the list.
     */
    @Test
    public void testEveryCandidateComesBackWhenThereIsMoreThanOne()
    {
        List<String> both = TrainControlUI.routesMatchingIn(ROUTES, "depot");

        assertEquals(both.size(), 2,
            "a fragment fitting two routes came back as " + both + " - Find Route cannot offer a "
            + "choice it has not been given");

        assertTrue(both.contains("Depot Entry") && both.contains("depot exit"),
            "the candidates are not the two routes the fragment fits: " + both);
    }

    /**
     * An exact name comes back ALONE, so nobody is asked to confirm what they typed in full.
     *
     * This is the same ordering as before seen from the other side: "Yard" fits "Yard" and "Yard
     * Bypass", and offering both would be asking somebody to pick the name they had just typed
     * completely.
     *
     * MUTATION: drop the early return from the exact-match loop in routesMatchingIn and this fails.
     */
    @Test
    public void testAnExactNameComesBackAlone()
    {
        assertEquals(TrainControlUI.routesMatchingIn(ROUTES, "Yard"), Arrays.asList("Yard"),
            "typing a route's full name offered a choice, because that name is also part of another "
            + "route's name - so the most definite thing a user can do is answered with a question");

        assertEquals(TrainControlUI.routesMatchingIn(ROUTES, "sidings").size(), 0,
            "a name no route carries produced candidates");

        assertEquals(TrainControlUI.routesMatchingIn(ROUTES, "  ").size(), 0,
            "a blank search produced candidates - every route contains the empty string, so without "
            + "this the whole list would be offered to somebody who typed nothing");
    }

    /**
     * The menu item exists, is mounted by hand, and is not declared inside a generated block.
     *
     * The NetBeans rule, and it has already cost this project twice: a field declared inside a
     * GEN-BEGIN block is deleted the next time the designer regenerates from the form, which is how
     * cropOverlay vanished on 2026-08-28. A menu item that disappears on somebody else's form edit
     * would take the whole feature with it and nothing would fail until a person went looking.
     */
    @Test
    public void testTheMenuItemIsMountedByHand() throws Exception
    {
        String ui = new String(Files.readAllBytes(
            new File("src/org/traincontrol/gui/TrainControlUI.java").toPath()), StandardCharsets.UTF_8);

        assertTrue(ui.contains("private void mountFindRoute()"),
            "mountFindRoute has gone, so nothing puts Find Route on the routes menu");

        assertTrue(ui.contains("mountFindRoute();"),
            "mountFindRoute is never called, so the menu item is built and never mounted");

        int declared = ui.indexOf("private javax.swing.JMenuItem findRouteMenuItem;");

        assertTrue(declared > 0, "findRouteMenuItem is no longer declared where this test can see it");

        int variables = ui.indexOf("// Variables declaration - do not modify");

        assertTrue(variables > 0, "the generated variables block has moved - this scan needs updating");

        assertTrue(declared < variables,
            "findRouteMenuItem is declared inside the generated variables block. The NetBeans designer "
            + "deletes anything there that is not one of its own components the next time the form is "
            + "regenerated - which is how cropOverlay disappeared twice - and Find Route would go with "
            + "it silently");
    }
}
