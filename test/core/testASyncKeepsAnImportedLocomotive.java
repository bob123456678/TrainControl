package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotiveCsv;
import org.traincontrol.util.Util;
import support.StandInStation;

/**
 * A sync with a Central Station (a stand-in on loopback, never a real one) after Import from CSV (FR-049): a locomotive the
 * station does not list stays, and one it lists under the same name and decoder takes the station's address as any local
 * locomotive does, keeping its runtime.
 *
 * MUTATION: make the sync drop locomotives the station does not list, or rebuild an adopted one without its runtime, and
 * this fails.
 */
public class testASyncKeepsAnImportedLocomotive
{
    private static final String HEADER = "Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,Start Year,End Year,RailwayName,Notes\n";

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
            + " .symbol=2\n .av=20\n .progmask=0x40\n .tachomax=80\n .xprot=32767\n").getBytes(StandardCharsets.UTF_8));

        String dataDir = System.getProperty(Util.DATA_DIR_PROPERTY);
        support.LayoutSandbox sandbox = null;
        StandInStation station = null;
        MarklinControlStation model = null;

        try
        {
            sandbox = support.LayoutSandbox.open(new File(root.toFile(), "none"));
            station = new StandInStation((path, n) -> path.equals("/config/geraet.vrs")
                ? "[geraet]\ngeraet\n .produkt=Central Station 2\n".getBytes(StandardCharsets.UTF_8)
                : StandInStation.file(station0.getPath() + path));
            System.setProperty(Util.DATA_DIR_PROPERTY, Files.createDirectory(root.resolve("data")).toString());
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
            model = support.TestStation.simulated();

            MarklinLocomotiveCsv.read(HEADER + "FR049 same name,,MM2,9,0,5:00:00,0,0,,\nFR049 only here,,MFX,300,0,2:00:00,0,0,,\n")
                .apply(model);

            station.forget();
            model.clearLayouts();

            String was = support.TestStationAddress.get();

            try
            {
                support.TestStationAddress.set(station.address());
                model.syncWithCS2();
            }
            finally
            {
                support.TestStationAddress.set(was);
            }

            Locomotive same = model.getLocByName("FR049 same name");
            Locomotive only = model.getLocByName("FR049 only here");

            assertNotNull(same, "the sync lost the imported locomotive the station lists");
            assertEquals(same.getAddress(), 12, "the sync did not give the imported locomotive the station's address");
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
