package regression;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * Importing a legacy autonomy.json leaves Startup -> Load Autonomy as the operator set it (Adam, 2026-09-24, TDD-C11).
 *
 * REG2-C3 unticked it after every import, on his ruling *"Set the setting to unchecked when importing a legacy json
 * file, each time"* - made while the box still loaded the old graph at start, which a layout that had only an import
 * did not have.  OB-254 took the old graph out of start-up the same night: the box now resumes the active diagram
 * configuration, and an import makes one, so the untick was what stopped the imported setup being loaded at the next
 * start.  Asked whether to keep it: *"Drop it now."*
 *
 * The import doors open modal dialogs and load the railway, so they are read rather than driven -
 * `testTheDestinationDoorsAgree`'s shape.
 *
 * MUTATION: have any import door write the preference or the menu item again, and this fails naming it.
 *
 * @author Adam
 */
public class testALegacyImportLeavesLoadAutonomyAlone
{
    /**
     * Every call of `importLegacy` in the window's code leaves Load Autonomy alone for the rest of its method.
     *
     * @throws Exception if the sources cannot be read
     */
    @Test
    public void testNoImportDoorTouchesLoadAutonomy() throws Exception
    {
        int doors = 0;

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(Paths.get("src/org/traincontrol/gui")))
        {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) walk::iterator)
            {
                if (!file.toString().endsWith(".java")) continue;

                String code = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    .replaceAll("(?s)/[*].*?[*]/", " ").replaceAll("//[^\\r\\n]*", " ");

                int at = code.indexOf(".importLegacy(");

                while (at >= 0)
                {
                    doors++;

                    // The rest of the method the call is in: to the next method-level closing brace.
                    int end = code.indexOf("\n    }", at);

                    String rest = code.substring(at, end < 0 ? code.length() : end);

                    for (String touch : new String[] {"autoLoadOffAfterLegacyImport(", "AUTO_LOAD_AUTONOMY",
                        "AutoLoadAutonomyMenuItem"})
                    {
                        assertFalse(rest.contains(touch), file.getFileName() + " imports a legacy autonomy.json and then"
                            + " changes Startup -> Load Autonomy (" + touch + ") - Adam, TDD-C11: \"Drop it now\"");
                    }

                    at = code.indexOf(".importLegacy(", at + 1);
                }
            }
        }

        assertTrue(doors > 0, "precondition: no import door was found under gui/, so the census checked nothing");
    }

    /**
     * And the untick itself is gone, so nothing can call it again.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testTheUntickIsGone() throws Exception
    {
        String window = new String(Files.readAllBytes(Paths.get("src/org/traincontrol/gui/TrainControlUI.java")),
            StandardCharsets.UTF_8);

        assertFalse(window.contains("void autoLoadOffAfterLegacyImport("), "TrainControlUI still has the method that"
            + " unticks Load Autonomy after a legacy import (TDD-C11)");
    }
}
