package regression;

import java.awt.Container;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.base.Route;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;
import support.Reflect;

/**
 * Routes > Import, driven as Adam drives it: the file chooser answered with an export of his routes, the question about
 * automatic firing answered No (MT-496) or Yes (MT-497), and what the door then says and leaves armed read.
 *
 * The file is `exportRoutes()`; the Export door itself is not pressed because it copies to the machine's clipboard.
 * An import deletes every route in the data it runs on, so this runs only where the run has its own copy of the data
 * (one.sh, battery.sh), as `core.testAnImportSaysItsRoutesAreOff` does.
 *
 * @author Adam
 */
public class testTheRoutesImportDoorAsksByName
{
    private static final String NO = String.valueOf(TrainControlUI.YES_NO_OPTS[1]);
    private static final String YES = String.valueOf(TrainControlUI.YES_NO_OPTS[0]);

    /**
     * Answered No: the question named the routes saved with automatic firing on and started on No, the message says
     * every route came in with it off, and none has it on (MT-496).
     *
     * MUTATION: leave the names out of the question's Details list, start it on Yes, or arm on No, and this fails.
     */
    @Test
    public void testAnsweredNoEveryRouteArrivesOff() throws Exception
    {
        Imported run = importHisRoutes(false, null);

        assertEquals(run.initial, NO, "the question does not start on No (MT-496)");

        // THE COUNT IN THE SENTENCE, THE NAMES IN THE DETAILS LIST UNDER IT
        assertEquals(run.questionShape.sentence, I18n.f("route.ui.confirmRearmImported", run.savedArmed.size()),
            "the question does not count the routes saved with automatic firing on: " + run.questionShape);

        assertEquals(run.questionShape.details, run.savedArmed, "the question does not name the routes saved with"
            + " automatic firing on (MT-496): " + run.questionShape);

        for (String name : run.armedBefore)
        {
            assertTrue(run.savedArmed.contains(name), "precondition: " + name + ", armed before the export, is not saved"
                + " armed in the file: " + run.savedArmed);
        }

        assertEquals(run.said, Collections.singletonList(I18n.f(MarklinControlStation.IMPORTED_ROUTES_NOTICE,
            run.routesBefore.size(), I18n.t("ui.main.bulkEnable"), I18n.t("route.ui.menuEnableAutoExecution"))),
            "answered No, the door does not say the routes came in with automatic firing off (MT-496)");

        assertEquals(run.routesAfter, run.routesBefore, "the import did not bring back the routes it was given");

        assertEquals(run.armedAfter, Collections.emptySet(), "answered No, routes arrived with automatic firing on"
            + " (MT-496)");
    }

    /**
     * Answered Yes: the message says automatic firing is on again for the routes saved with it on, and exactly those
     * have it (MT-497).
     *
     * MUTATION: arm none, arm every route, or say another count, and this fails.
     */
    @Test
    public void testAnsweredYesTheSavedArmedRoutesAreArmedAgain() throws Exception
    {
        Imported run = importHisRoutes(true, null);

        assertEquals(run.said, Collections.singletonList(I18n.f(MarklinControlStation.IMPORTED_ROUTES_REARMED,
            run.routesBefore.size(), run.armedBefore.size())), "answered Yes, the door does not say automatic firing is"
            + " on again for the routes saved with it on (MT-497)");

        assertEquals(run.routesAfter, run.routesBefore, "the import did not bring back the routes it was given");

        assertEquals(run.armedAfter, run.armedBefore, "answered Yes, the routes with automatic firing on are not exactly the"
            + " ones armed before the export (MT-497)");
    }

    /**
     * A route saved armed that has no sensor to watch is not armed by Yes (the model arms only a route with one), and
     * the door neither counts it as armed again nor names it in the question: it counted N from the file while the
     * model counted the routes it armed.
     *
     * MUTATION: count from the file again, or name every route saved armed, and this fails.
     */
    @Test
    public void testASavedArmedRouteWithNoSensorIsNotCountedAsArmed() throws Exception
    {
        final String[] noSensor = new String[1];

        Imported run = importHisRoutes(true, file ->
        {
            // ONE MORE ROUTE SAVED ARMED, of those with no sensor.
            JSONObject json = new JSONObject(file);

            JSONArray routes = json.getJSONArray("routes");

            for (int i = 0; i < routes.length(); i++)
            {
                JSONObject route = routes.getJSONObject(i);

                if (!route.has("s88") && !route.optBoolean("auto", false))
                {
                    route.put("auto", true);

                    noSensor[0] = route.getString("name");

                    return json.toString();
                }
            }

            throw new AssertionError("every route in this data has a sensor, so none can be saved armed without one");
        });

        assertNotNull(noSensor[0], "precondition: no route with no sensor was saved armed in the file");

        // THE QUESTION ON SCREEN, not the model's list: the names in its Details list.
        List<String> listed = run.questionShape.details;

        assertFalse(listed.contains(noSensor[0]), "the question names " + noSensor[0]
            + ", saved armed with no sensor, among the routes Yes turns automatic firing on for - and Yes does not"
            + " (RLU-C2): " + run.question);

        assertEquals(listed, run.savedArmed, "the question does not name exactly the"
            + " routes the import arms: " + run.question);

        assertEquals(run.armedAfter, run.armedBefore, "answered Yes, a route saved armed with no sensor was armed, or a"
            + " route with one was not");

        assertEquals(run.said, Collections.singletonList(I18n.f(MarklinControlStation.IMPORTED_ROUTES_REARMED,
            run.routesBefore.size(), run.armedBefore.size())), "the door counts a route it could not arm as armed again");
    }

    /**
     * A route whose name has a letter outside ASCII comes back by the name it was exported with: Export writes UTF-8
     * and the door read it in the machine's character set (a code page on Java 8 under Windows), so a non-ASCII
     * locomotive command was skipped when the route fired.  On a UTF-8 JVM the two agree and this is skipped rather
     * than passed.
     *
     * MUTATION: read the file without a character set, and this fails.
     */
    @Test
    public void testARouteNamedOutsideAsciiComesBackByItsName() throws Exception
    {
        if (StandardCharsets.UTF_8.equals(java.nio.charset.Charset.defaultCharset()))
        {
            throw new SkipException("this JVM reads UTF-8 by default, so a file read without a character set comes back"
                + " right and this cannot tell the two apart");
        }

        final String[] renamed = new String[2];

        Imported run = importHisRoutes(true, file ->
        {
            // ONE ROUTE SAVED ARMED, RENAMED WITH A LETTER A CODE PAGE READS AS TWO.
            JSONObject json = new JSONObject(file);

            JSONArray routes = json.getJSONArray("routes");

            for (int i = 0; i < routes.length(); i++)
            {
                JSONObject route = routes.getJSONObject(i);

                if (route.optBoolean("auto", false))
                {
                    renamed[0] = route.getString("name");
                    renamed[1] = renamed[0] + " S\u00fcd";

                    route.put("name", renamed[1]);

                    return json.toString();
                }
            }

            throw new AssertionError("precondition: no route is saved armed in the file");
        });

        assertTrue(run.savedArmed.contains(renamed[1]), "precondition: the file does not hold the renamed route");

        Set<String> expected = new TreeSet<>(run.routesBefore);

        expected.remove(renamed[0]);
        expected.add(renamed[1]);

        assertEquals(run.routesAfter, expected, "Routes > Import did not bring the route back as \"" + renamed[1] + "\":"
            + " the file is UTF-8, and a letter outside ASCII read in another character set comes back as other letters"
            + " (RLU-A1)");
    }

    /**
     * The notice naming the routes an import split fits the screen too: short paragraphs over the names in a Details
     * list, in a popup no wider than about 640 px.  The file carries one route that mixes an emergency stop with other
     * commands, which the import splits.
     *
     * MUTATION: put the split routes back into the notice's text, and this fails.
     */
    @Test
    public void testTheLineNamingTheRoutesItSplitFitsTheScreen() throws Exception
    {
        Imported run = importHisRoutes(false, file ->
        {
            // ONE ROUTE WITH COMMANDS, COPIED WITH AN EMERGENCY STOP AMONG THEM
            JSONObject json = new JSONObject(file);

            JSONArray routes = json.getJSONArray("routes");

            for (int i = 0; i < routes.length(); i++)
            {
                JSONObject route = routes.getJSONObject(i);

                if (route.optJSONArray("commands") != null && route.getJSONArray("commands").length() > 0)
                {
                    JSONObject mixed = new JSONObject(route.toString());

                    mixed.put("name", "RL5 a route that mixes a stop with its other commands");
                    mixed.put("id", 99977);
                    mixed.put("auto", false);
                    mixed.getJSONArray("commands").put(new JSONObject().put("type", "TYPE_STOP")
                        .put("state", new JSONObject()));

                    routes.put(mixed);

                    break;
                }
            }

            return json.toString();
        });

        String opening = words(I18n.t("route.ui.infoImportSplitStopRoutes"));

        support.PopupShape told = null;

        for (support.PopupShape shown : run.saidShapes)
        {
            if (words(shown.sentence).contains(opening)) told = shown;
        }

        assertNotNull(told, "the import did not say it split a route, in one sentence over a list: " + run.said);

        // THE NAMES IN THE DETAILS LIST, and each paragraph above it one short sentence
        assertTrue(told.details.contains("RL5 a route that mixes a stop with its other commands"), "the route the import"
            + " split is not in the Details list under the notice: " + told);

        for (String paragraph : told.sentence.split("\n\n"))
        {
            assertTrue(support.PopupShape.words(paragraph) <= 30, "a paragraph above the list is "
                + support.PopupShape.words(paragraph) + " words long: " + told);
        }

        assertTrue(told.width <= support.PopupShape.MAX_WIDTH, "the notice naming the routes the import split is "
            + told.width + " px wide, so the dialog is as wide as that list (RLU5-C4): " + told);
    }

    /**
     * The question fits the screen however many routes it names: one short sentence with their count, the names in a
     * scrolling Details list under it, in a popup no wider than about 640 px.  Here every route with a sensor is saved
     * armed, which made a question of over 200 words.
     *
     * MUTATION: put the names back into the question's text, and this fails.
     */
    @Test
    public void testAQuestionNamingManyRoutesFitsTheScreen() throws Exception
    {
        Imported run = importHisRoutes(false, file ->
        {
            // EVERY ROUTE WITH A SENSOR SAVED ARMED.
            JSONObject json = new JSONObject(file);

            JSONArray routes = json.getJSONArray("routes");

            for (int i = 0; i < routes.length(); i++)
            {
                if (routes.getJSONObject(i).has("s88")) routes.getJSONObject(i).put("auto", true);
            }

            return json.toString();
        });

        assertTrue(String.join(", ", run.savedArmed).length() > 200, "precondition: the routes saved armed make a list of"
            + " only " + String.join(", ", run.savedArmed).length() + " characters, which fits on one line anyway");

        // ONE SHORT SENTENCE, THE NAMES UNDER IT IN A DETAILS LIST, AND A DIALOG THAT FITS
        assertEquals(run.questionShape.sentence, I18n.f("route.ui.confirmRearmImported", run.savedArmed.size()),
            "the question is not its sentence with the count: " + run.questionShape);

        assertEquals(run.questionShape.details, run.savedArmed, "the Details list does not name the routes saved armed: "
            + run.questionShape);

        assertTrue(run.questionShape.width <= support.PopupShape.MAX_WIDTH, "the question is " + run.questionShape.width
            + " px wide, as wide as its list of routes, and can run off the screen (RLU-C3): " + run.questionShape);
    }

    /**
     * Once the file is picked, Import asks "This replaces all N routes. Continue?" with the count of routes there are,
     * starting on No; No leaves every route as it was, armed or not, and asks and says nothing more.
     *
     * MUTATION: drop the question from `importRoutesMenuItemActionPerformed`, start it on Yes, or go on after No, and this
     * fails.
     */
    @Test
    public void testNoToTheReplaceQuestionLeavesEveryRoute() throws Exception
    {
        Imported run = importHisRoutes(true, null, false, -1);

        assertEquals(run.replaceAsked, Collections.singletonList(replacing(run.routesBefore.size())),
            "Import did not ask once whether to replace the routes, counting them (D51)");

        assertEquals(run.replaceStartsOn, NO, "the question about replacing every route does not start on No");

        assertEquals(run.routesAfter, run.routesBefore, "answered No, the routes changed");

        assertEquals(run.armedAfter, run.armedBefore, "answered No, which routes fire automatically changed");

        assertEquals(run.question, null, "answered No, Import went on to ask about automatic firing");

        assertEquals(run.said, Collections.emptyList(), "answered No, Import went on and said: " + run.said);
    }

    /**
     * With no routes there is nothing to replace, so Import does not ask, and the file's routes arrive.
     *
     * MUTATION: ask whatever the count, and this fails.
     */
    @Test
    public void testWithNoRoutesTheReplaceQuestionIsNotAsked() throws Exception
    {
        Imported run = importHisRoutes(true, null, true, 0);

        assertEquals(run.replaceAsked, Collections.emptyList(), "with no routes, Import asked whether to replace them");

        assertEquals(run.routesAfter, run.routesBefore, "the import did not bring back the routes it was given");
    }

    /**
     * With one route the question says so in the singular, and with three it counts them, each in the language's own
     * grammar; titled with the menu item's words, Import Routes.  Answered Yes, the file's routes arrive.
     *
     * MUTATION: ask "This replaces all 1 routes", or title the question Confirm, and this fails.
     */
    @Test
    public void testTheReplaceQuestionCountsOneRouteAndThree() throws Exception
    {
        for (int keep : new int[] {1, 3})
        {
            Imported run = importHisRoutes(true, null, true, keep);

            assertEquals(run.replaceAsked, Collections.singletonList(replacing(keep)), "with " + keep + " route(s),"
                + " Import's question does not read as it should");

            assertEquals(run.replaceTitle, I18n.t("route.ui.titleImportRoutes"), "the question is not titled Import"
                + " Routes");

            assertEquals(run.routesAfter, run.routesBefore, "answered Yes, the file's routes did not arrive");
        }

        assertEquals(replacing(1), I18n.t("route.ui.confirmReplaceTheOneRoute"), "precondition: one route is not the"
            + " singular sentence");
    }

    /** The question Import asks with this many routes, as the bundle says it. */
    private static String replacing(int routes)
    {
        return routes == 1 ? I18n.t("route.ui.confirmReplaceTheOneRoute")
            : I18n.f("route.ui.confirmReplaceAllRoutes", routes);
    }

    /** A popup's text: its sentence, then each line of its Details list. */
    private static String textOf(support.PopupShape shown)
    {
        return shown.details.isEmpty() ? shown.sentence : shown.sentence + "\n" + String.join("\n", shown.details);
    }

    /** A sentence as its words, whatever lines they are on. */
    private static String words(String text)
    {
        return text.replaceAll("\\s+", " ").trim();
    }

    // ---------------------------------------------------------------- the door

    /** What one import through the door did. */
    private static final class Imported
    {
        Set<String> routesBefore;
        Set<String> armedBefore;
        List<String> savedArmed;
        String question;
        support.PopupShape questionShape;
        String initial;
        List<String> said;
        List<support.PopupShape> saidShapes;
        Set<String> routesAfter;
        Set<String> armedAfter;
        List<String> replaceAsked;
        String replaceStartsOn;
        String replaceTitle;
    }

    /** A change made to the exported file before it is imported, as a person editing it would make it. */
    private interface Edit
    {
        String to(String file) throws Exception;
    }

    /**
     * Arms a route with a sensor where none is armed (MT-496 and MT-497 step 1), exports, and imports the file through
     * Routes > Import, answering the question.
     */
    private static Imported importHisRoutes(boolean yes, Edit edit) throws Exception
    {
        Imported run = importHisRoutes(yes, edit, true, -1);

        assertEquals(run.replaceAsked, Collections.singletonList(replacing(run.routesBefore.size())),
            "Import did not ask once whether to replace the routes, counting them (D51)");

        return run;
    }

    /**
     * The same, answering the question about replacing every route with `replace`, and with `keep` at 0 or more
     * deleting all but that many routes after the export (-1 keeps them all).
     */
    private static Imported importHisRoutes(boolean yes, Edit edit, boolean replace, int keep)
        throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        if (System.getProperty(Util.DATA_DIR_PROPERTY) == null)
        {
            throw new SkipException("an import deletes every route in LocDB.data, so this runs only where the run has its"
                + " own copy of the data (one.sh, battery.sh)");
        }

        final TrainControlUI[] ui = new TrainControlUI[1];

        final support.SandboxedWindow sandboxed = new support.SandboxedWindow();

        File file = File.createTempFile("tc-routes-export", ".json");

        Answerer answerer = null;

        try
        {
            sandboxed.hold(support.LayoutSandbox.open());

            ui[0] = sandboxed.hold(openTheWindow());

            MarklinControlStation model = (MarklinControlStation) ui[0].getModel();

            assertFalse(model.getRouteList().isEmpty(), "precondition: this data has no routes to export");

            Imported run = new Imported();

            if (armed(model).isEmpty())
            {
                String withASensor = null;

                for (String name : model.getRouteList())
                {
                    if (model.getRoute(name).hasS88()) withASensor = name;
                }

                assertNotNull(withASensor, "precondition: no route in this data has a sensor condition to arm");

                final String arming = withASensor;

                SwingUtilities.invokeAndWait(() -> ui[0].enableOrDisableRoute(arming, true));

                // THE EDIT DELETES AND RE-ADDS THE ROUTE, so for a moment it is in the list under no name at all, and
                // getRoute answered null.
                for (long end = System.currentTimeMillis() + 10000; (model.getRoute(arming) == null
                    || !model.getRoute(arming).isEnabled()) && System.currentTimeMillis() < end; ) Thread.sleep(50);
            }

            run.routesBefore = new TreeSet<>(model.getRouteList());
            run.armedBefore = armed(model);

            assertFalse(run.armedBefore.isEmpty(), "precondition: no route has automatic firing on to export");

            String json = model.exportRoutes();

            if (edit != null) json = edit.to(json);

            Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));

            run.savedArmed = model.routesSavedArmed(json);

            if (keep >= 0)
            {
                List<String> names = new ArrayList<>(model.getRouteList());

                assertTrue(names.size() > keep, "precondition: this data has only " + names.size() + " routes");

                for (String name : names.subList(keep, names.size())) model.deleteRoute(name);

                final int kept = keep;

                support.Components.waitFor(() -> model.getRouteList().size() == kept, 30000);

                assertEquals(model.getRouteList().size(), keep, "precondition: the routes could not be deleted");
            }

            answerer = new Answerer(file, yes, replacing(model.getRouteList().size()), replace);

            Thread answering = new Thread(answerer, "routes import answerer");

            answering.setDaemon(true);
            answering.start();

            java.lang.reflect.Field item = Reflect.field(TrainControlUI.class, "importRoutesMenuItem");

            final javax.swing.JMenuItem importRoutes = (javax.swing.JMenuItem) item.get(ui[0]);

            SwingUtilities.invokeLater(importRoutes::doClick);

            // DONE WHEN THE DOOR HAS SAID WHAT IT DID: the message after the busy dialog, or, answered No to replacing
            // the routes, a moment after that answer, in which anything it went on to do would have shown.
            final Answerer asked = answerer;
            final boolean yesToReplace = replace;

            support.Components.waitFor(() -> !asked.said.isEmpty()
                || (!yesToReplace && !asked.replaceAsked.isEmpty()), 180000);

            // Answered No: give it the moment in which a door that went on anyway would have said so
            if (!replace) support.Components.waitFor(() -> !asked.said.isEmpty(), 2000);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(answerer.chose, "precondition: Routes > Import never showed the file chooser");

            run.replaceAsked = new ArrayList<>(answerer.replaceAsked);
            run.replaceStartsOn = answerer.replaceStartsOn;
            run.replaceTitle = answerer.replaceTitle;

            if (replace)
            {
                assertNotNull(answerer.question, "Routes > Import never asked about automatic firing, over a file with"
                    + " routes saved with it on");
            }

            run.question = answerer.question;
            run.questionShape = answerer.questionShape;
            run.initial = answerer.initial;
            run.said = new ArrayList<>(answerer.said);
            run.saidShapes = new ArrayList<>(answerer.saidShapes);
            run.routesAfter = new TreeSet<>(model.getRouteList());
            run.armedAfter = armed(model);

            return run;
        }
        finally
        {
            if (answerer != null) answerer.running = false;

            sandboxed.close();

            if (!file.delete()) file.deleteOnExit();
        }
    }

    private static Set<String> armed(MarklinControlStation model)
    {
        Set<String> out = new TreeSet<>();

        for (String name : model.getRouteList())
        {
            Route route = model.getRoute(name);

            if (route != null && route.isEnabled()) out.add(name);
        }

        return out;
    }

    private static TrainControlUI openTheWindow() throws Exception
    {
        MarklinControlStation model = support.TestStation.simulatedInDebug();

        final TrainControlUI[] made = new TrainControlUI[1];

        SwingUtilities.invokeAndWait(() -> made[0] = new TrainControlUI());

        made[0].setViewListener(model, new CountDownLatch(1));

        final CountDownLatch settled = new CountDownLatch(1);

        made[0].whenTilesSettled(() -> settled.countDown());

        settled.await(30, TimeUnit.SECONDS);

        for (int turn = 0; turn < 4; turn++) SwingUtilities.invokeAndWait(() -> { });

        return made[0];
    }

    /** Answers the door's dialogs as they appear: the file, the question, and OK to every message. */
    private static final class Answerer implements Runnable
    {
        private final File file;
        private final boolean yes;
        private final List<String> said = Collections.synchronizedList(new ArrayList<>());
        private final Set<Object> handled = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private volatile boolean running = true;
        private volatile boolean chose = false;
        private volatile String question;
        private volatile support.PopupShape questionShape;
        private final List<support.PopupShape> saidShapes = Collections.synchronizedList(new ArrayList<>());
        private volatile String initial;
        private final String replaceQuestion;
        private final boolean replace;
        private final List<String> replaceAsked = Collections.synchronizedList(new ArrayList<>());
        private volatile String replaceStartsOn;
        private volatile String replaceTitle;

        Answerer(File file, boolean yes, String replaceQuestion, boolean replace)
        {
            this.file = file;
            this.yes = yes;
            this.replaceQuestion = replaceQuestion;
            this.replace = replace;
        }

        @Override
        public void run()
        {
            support.DialogWatch.until(() -> running, 150, dialog ->
            {
                final JFileChooser chooser = find(dialog.getContentPane(), JFileChooser.class);

                if (chooser != null)
                {
                    if (!handled.add(chooser)) return;

                    chose = true;

                    SwingUtilities.invokeLater(() ->
                    {
                        chooser.setSelectedFile(file);
                        chooser.approveSelection();
                    });

                    return;
                }

                final JOptionPane pane = find(dialog.getContentPane(), JOptionPane.class);

                if (pane == null || !handled.add(pane)) return;

                if (replaceQuestion.equals(String.valueOf(pane.getMessage())))
                {
                    replaceAsked.add(replaceQuestion);
                    replaceStartsOn = String.valueOf(pane.getInitialValue());
                    replaceTitle = dialog.getTitle();

                    final String answer = replace ? YES : NO;

                    press(pane, answer);

                    return;
                }

                if (I18n.t("route.ui.confirmRearmImportedTitle").equals(dialog.getTitle()))
                {
                    questionShape = support.PopupShape.of(pane);
                    question = textOf(questionShape);
                    initial = String.valueOf(pane.getInitialValue());

                    final String answer = yes ? YES : NO;

                    press(pane, answer);

                    return;
                }

                support.PopupShape shown = support.PopupShape.of(pane);

                saidShapes.add(shown);
                said.add(textOf(shown));

                SwingUtilities.invokeLater(() -> pane.setValue(Integer.valueOf(JOptionPane.OK_OPTION)));
            });
        }
    }

    /** Presses the question's button of this text. */
    private static void press(final JOptionPane pane, final String answer)
    {
        SwingUtilities.invokeLater(() ->
        {
            for (Object option : pane.getOptions())
            {
                if (answer.equals(String.valueOf(option))) pane.setValue(option);
            }
        });
    }

    private static <T> T find(Container container, Class<T> type)
    {
        return support.Components.inside(container, type);
    }
}
