package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotiveCsv;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;
import support.StandInStation;

/**
 * A sync with a Central Station (a stand-in on loopback, never a real one) after Import from CSV (FR-049): a locomotive the
 * station does not list stays, and one it lists under the same name and decoder at another address keeps its own and
 * its runtime, as any local locomotive does in a sync nobody pressed (FR-246), logged as that and not as a duplicate;
 * one under the same name and another decoder is the duplicate.
 *
 * MUTATION: make the sync drop locomotives the station does not list, or take the station's address unasked, or in
 * `MarklinControlStation.syncWithCS2` leave out the `addressedElsewhere(l) == null` check, and this fails.
 */
public class testASyncKeepsAnImportedLocomotive
{
    private static final String HEADER = "Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,Start Year,End Year,RailwayName,Notes,LastRun\n";

    @Test
    public void testASyncKeepsAnImportedLocomotiveAndItsRuntime() throws Exception
    {
        Path root = Files.createTempDirectory("tc-import-sync");
        File station0 = new File(root.toFile(), "A");
        File config = new File(station0, "config");

        new File(config, "gleisbilder").mkdirs();

        for (String file : new String[] {"gleisbild.cs2", "magnetartikel.cs2", "gleisbilder/test.cs2"})
        {
            Files.write(new File(config, file).toPath(), Files.readAllBytes(new File("test/layout/config/" + file).toPath()));
        }

        Files.write(new File(config, "fahrstrassen.cs2").toPath(), Files.readAllBytes(new File("test/fahrstrassen.cs2").toPath()));
        Files.write(new File(config, "lokomotive.cs2").toPath(), ("[lokomotive]\nversion\n .minor=3\nsession\n .id=35361\n"
            + "lokomotive\n .name=FR049 same name\n .richtung=1\n .uid=0xC\n .adresse=0xC\n .typ=mm2_dil8\n .icon=BR 85\n"
            + " .symbol=2\n .av=20\n .progmask=0x40\n .tachomax=80\n .xprot=32767\n"
            + "lokomotive\n .name=FR049 other decoder\n .richtung=1\n .uid=0x14\n .adresse=0x14\n .typ=mm2_dil8\n"
            + " .icon=BR 85\n .symbol=2\n .av=20\n .progmask=0x40\n .tachomax=80\n .xprot=32767\n").getBytes(StandardCharsets.UTF_8));

        String dataDir = System.getProperty(Util.DATA_DIR_PROPERTY);
        support.LayoutSandbox sandbox = null;
        StandInStation station = null;
        MarklinControlStation model = null;
        List<String> logged = Collections.synchronizedList(new ArrayList<>());
        Handler tap = support.LogTap.messagesInto(logged);

        try
        {
            sandbox = support.LayoutSandbox.open(new File(root.toFile(), "none"));
            station = new StandInStation((path, n) -> path.equals("/config/geraet.vrs")
                ? "[geraet]\ngeraet\n .produkt=Central Station 2\n".getBytes(StandardCharsets.UTF_8)
                : StandInStation.file(station0.getPath() + path));
            System.setProperty(Util.DATA_DIR_PROPERTY, Files.createDirectory(root.resolve("data")).toString());
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
            model = support.TestStation.simulated();

            MarklinLocomotiveCsv csv = MarklinLocomotiveCsv.read(HEADER + "FR049 same name,,MM2,9,0,5:00:00,0,0,,,2024-02-02\nFR049 only here,,MFX,300,0,2:00:00,0,0,,,2024-02-02\n"
                + "FR049 other decoder,,MFX,400,0,1:00:00,0,0,,,2024-02-02\n");

            csv.apply(model, csv.plan(model));

            station.forget();
            model.clearLayouts();

            String was = support.TestStationAddress.get();

            try
            {
                support.TestStationAddress.set(station.address());
                Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);
                model.syncWithCS2();
            }
            finally
            {
                Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(tap);
                support.TestStationAddress.set(was);
            }

            Locomotive same = model.getLocByName("FR049 same name");
            Locomotive only = model.getLocByName("FR049 only here");

            assertNotNull(same, "the sync lost the imported locomotive the station lists");
            assertEquals(same.getAddress(), 9, "a sync nobody pressed gave the imported locomotive the station's address");
            assertTrue(logged.contains(I18n.f("loc.addressDiffersAtStation", "FR049 same name", 12, 9)),
                "the sync did not log the station's other address: " + logged);
            assertFalse(logged.contains(I18n.f("loc.importSkippedDuplicateName", "FR049 same name")),
                "the sync logged the same locomotive at another address as a duplicate");
            assertTrue(logged.contains(I18n.f("loc.importSkippedDuplicateName", "FR049 other decoder")),
                "the sync did not log the duplicate name: " + logged);
            assertEquals(model.getLocByName("FR049 other decoder").getAddress(), 400, "the duplicate replaced it");
            assertEquals(same.getTotalRuntime(), 18000000L, "the sync changed the imported locomotive's runtime");
            assertNotNull(only, "the sync dropped an imported locomotive the station does not list");
            assertEquals(only.getAddress(), 300);
            assertEquals(only.getTotalRuntime(), 7200000L);
            assertTrue(model.getLocomotives().stream().filter(l -> l.getName().equals("FR049 same name")).count() == 1,
                "the sync made a second locomotive of the same name");
        }
        finally
        {
            StandInStation.tearDown(station, model, sandbox);

            if (dataDir == null)
            {
                System.clearProperty(Util.DATA_DIR_PROPERTY);
            }
            else
            {
                System.setProperty(Util.DATA_DIR_PROPERTY, dataDir);
            }
        }
    }
}
