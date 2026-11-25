package ui;

import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.LayoutPopupWindow;
import org.traincontrol.gui.route.RouteEditorWindow;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.locomotive.LocomotiveSelector;
import org.traincontrol.gui.locomotive.UsageChart;
import org.traincontrol.util.I18n;
import support.AskingWindows;
import support.Components;
import support.FrozenEditor;
import support.LanguageFit;
import support.Reflect;

/**
 * `testEveryLanguageFits` for the editors and the other windows the menus open, on the frozen railway: the track editor
 * and the autonomy editor (palette, side panel and its tabs), the route editor, the page's own window, the locomotive
 * selector and the usage chart.  Each is built in each language at the size it opens at and measured by
 * `support.LanguageFit`.
 *
 * MUTATION: give a button of the window a claim opens a fixed preferred width of 20 pixels, and that claim fails in every
 * language.
 */
public class testEveryLanguageFitsTheEditors
{
    private static final String PAGE = "1 - Main";

    private static final LanguageFit.Ledger LEDGER = new LanguageFit.Ledger();

    private static LanguageFit.Railway railway;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        railway = LanguageFit.Railway.open("live-snapshot");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (railway != null) railway.close(LEDGER, "editors");
    }

    /** The track editor fits in every language. */
    @Test(timeOut = 240000)
    public void testTheTrackEditorFitsInEveryLanguage() throws Exception
    {
        LanguageFit.assertNothingClipped("track-editor", LanguageFit.everyLanguage(language ->
            LanguageFit.measureBuilt(LEDGER, "track-editor", language, () ->
            {
                LayoutEditor built = new LayoutEditor(railway.model.getLayout(PAGE), 30, railway.ui, 0);

                built.render();

                return built;
            }, 15, FrozenEditor::dispose)));
    }

    /** The autonomy editor, its side panel on every tab, fits in every language. */
    @Test(timeOut = 240000)
    public void testTheAutonomyEditorFitsInEveryLanguage() throws Exception
    {
        LanguageFit.assertNothingClipped("autonomy-editor", LanguageFit.everyLanguage(language ->
        {
            LayoutEditor editor = FrozenEditor.editorOn(railway.model, railway.ui, railway.session, PAGE);

            try
            {
                return LanguageFit.measureNow(LEDGER, "autonomy-editor", language, editor, 30);
            }
            finally
            {
                FrozenEditor.dispose(editor);
            }
        }));
    }

    /** The route editor on one of the railway's routes fits in every language. */
    @Test(timeOut = 240000)
    public void testTheRouteEditorFitsInEveryLanguage() throws Exception
    {
        List<String> routes = railway.model.getRouteList();

        assertFalse(routes.isEmpty(), "precondition: the railway has no route to open");

        String name = routes.get(0);

        LanguageFit.assertNothingClipped("route-editor", LanguageFit.everyLanguage(language ->
            LanguageFit.measureBuilt(LEDGER, "route-editor", language, () -> shown(new RouteEditorWindow(railway.ui, name,
                railway.model.getRoute(name))), 15, LanguageFit::dispose)));
    }

    /**
     * The page's own window at the Layout tab's Small size fits in every language.  Built as `showLayoutPopup` builds
     * it (drawn, its captions written, then shown): the Small button looks its size up by its English name, which a
     * language changed after start-up misses.
     */
    @Test(timeOut = 240000)
    public void testThePageWindowFitsInEveryLanguage() throws Exception
    {
        @SuppressWarnings("unchecked")
        int small = ((Map<String, Integer>) Reflect.get(TrainControlUI.class, railway.ui, "layoutSizes"))
            .get(I18n.t("layout.ui.small"));

        LanguageFit.assertNothingClipped("page-window", LanguageFit.everyLanguage(language ->
            LanguageFit.measureBuilt(LEDGER, "page-window", language, () ->
            {
                LayoutPopupWindow built = new LayoutPopupWindow(railway.model.getLayout(PAGE), small, railway.ui);

                built.render();
                railway.ui.updateVisiblePoints();
                built.setVisible(true);

                return built;
            }, 0, AskingWindows::closeThePage)));
    }

    /** The locomotive selector and the usage chart fit in every language. */
    @Test(timeOut = 240000)
    public void testTheSelectorAndTheUsageChartFitInEveryLanguage() throws Exception
    {
        LanguageFit.assertNothingClipped("selector-and-chart", LanguageFit.everyLanguage(language ->
        {
            List<String> found = LanguageFit.measureBuilt(LEDGER, "selector", language, () ->
            {
                LocomotiveSelector built = shown(new LocomotiveSelector(railway.model, railway.ui));

                built.updateScrollArea();

                return built;
            }, 5, LanguageFit::dispose);

            found.addAll(LanguageFit.measureBuilt(LEDGER, "usage-chart", language, () -> shown(new UsageChart(railway.ui)), 5,
                LanguageFit::dispose));

            return found;
        }));
    }

    /** A window the caller shows, shown. */
    private static <T extends java.awt.Window> T shown(T window)
    {
        window.setVisible(true);

        return window;
    }
}
