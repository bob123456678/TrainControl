package ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import support.Answering;
import support.StandInStation;
import support.StationRig;

/**
 * The sync says in plain words when part of what the Central Station sent could not be read and was left as it was
 * (FR-169): the dialog the Sync menu raises, and the notice a start-up raises.  The sync is made through the real window
 * against a stand-in CS3, and the questions the program asks are read as the user would see them.
 *
 * MUTATION: have TrainControlUI.doSync always pick "ui.infoSyncCompleteItemsAdded" for its text, and the sync with an
 * unreadable route list says "Sync complete. Items added: 0" and nothing more; drop the `view.showSyncPartial()` call
 * of MarklinControlStation.init, and the start-up says nothing.
 *
 * @author Adam
 */
public class testTheSyncSaysWhenPartOfTheDataWasNotRead
{
    private static final String INFO = "{\"softwareVersion\":\"2.6.1 (Build 3)\"}";

    @Test
    public void testTheDialogAndTheStartUpNoticeSayPartWasLeftAsItWas() throws Exception
    {
        Map<String, byte[]> replaced = new ConcurrentHashMap<>();
        BiFunction<String, Integer, byte[]> base = StandInStation.cs3("260", INFO);

        try (StationRig rig = new StationRig(Files.createTempDirectory("tc-sync-says").toFile(),
            (path, n) -> replaced.containsKey(path) ? replaced.get(path) : base.apply(path, n), true))
        {
            TrainControlUI ui = (TrainControlUI) rig.model.getGUI();
            assertNotNull(ui, "the window");

            try
            {
                // One answerer for the whole claim: a dialog just answered is still showing for a moment, and a new answerer
                // would count it again
                try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
                {
                    assertTrue(rig.model.syncWithCS2() > 0, "the first sync imports the fixtures");

                    ui.doSync(ui);
                    assertTrue(answering.awaitAsked(1, 60000), "a sync that read everything asked nothing");

                    // The routes answered as a web page: nothing is read of them, and what is held is left
                    replaced.put("/app/api/automatics", "<html>not json</html>".getBytes(StandardCharsets.UTF_8));
                    ui.doSync(ui);
                    assertTrue(answering.awaitAsked(2, 60000), "the sync that read part asked nothing");
                    replaced.clear();

                    ui.showSyncPartial();
                    assertTrue(answering.awaitAsked(3, 60000), "the start-up notice asked nothing");

                    String plain = answering.asked().get(0);
                    String partial = answering.asked().get(1);
                    String notice = answering.asked().get(2);

                    assertEquals(plain, I18n.f("ui.infoSyncCompleteItemsAdded", "0"), "a sync that read everything says so");
                    assertEquals(partial, I18n.f("ui.infoSyncCompletePartial", "0"));
                    assertTrue(partial.contains("could not be read") && partial.contains("log"), "it says part was not read: " + partial);
                    assertTrue(partial.contains("Items added: 0"), "and still says what was added: " + partial);
                    assertEquals(notice, I18n.t("ui.startupSyncPartial"));
                    assertTrue(notice.contains("could not be read") && notice.contains("sync again"), "start-up says so: " + notice);
                }
            }
            finally
            {
                SwingUtilities.invokeAndWait(ui::dispose);
            }
        }
    }

    /** The start-up's own sync, made by the application's init against a station whose routes cannot be read, raises the notice. */
    @Test
    public void testTheStartUpSaysSoToo() throws Exception
    {
        BiFunction<String, Integer, byte[]> base = StandInStation.cs3("260", INFO);

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            try (StationRig rig = new StationRig(Files.createTempDirectory("tc-startup-says").toFile(),
                (path, n) -> "/app/api/automatics".equals(path) ? "<html>not json</html>".getBytes(StandardCharsets.UTF_8)
                    : base.apply(path, n), true, true))
            {
                TrainControlUI ui = (TrainControlUI) rig.model.getGUI();

                try
                {
                    assertTrue(answering.awaitAsked(1, 60000), "start-up asked nothing");
                    assertEquals(answering.asked().get(0), I18n.t("ui.startupSyncPartial"));
                }
                finally
                {
                    SwingUtilities.invokeAndWait(ui::dispose);
                }
            }
        }
    }
}
